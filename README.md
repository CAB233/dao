# 倒班表

倒班表是一款面向轮班、倒班场景的安卓应用。用户可以创建常用班次模板，按周期安排每日班次，并通过月历直观查看排班结果。

## 主要功能

- 自定义班次名称、时间与颜色
- 创建多个周期倒班方案
- 在月历中查看每日班次
- 支持跨零点夜班
- 通过二维码、剪贴板分享与导入方案
- 数据保存在本地，可离线使用

## 构建项目

构建环境要求：

- Java 开发工具包 17 或更高版本
- Android SDK 37
- 可联网下载项目依赖（项目已自带 Gradle Wrapper）

构建调试安装包：

```bash
./gradlew :app:assembleDebug
```

构建正式版安装包：

```bash
./gradlew :app:assembleRelease
```

## 致谢

- [miuix](https://github.com/compose-miuix-ui/miuix)：提供界面组件与设计基础
- [ZXing](https://github.com/zxing/zxing)：提供二维码生成与扫描能力

## 开源许可

[AGPL-3.0](LICENSE)

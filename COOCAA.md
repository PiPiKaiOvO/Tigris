# 酷开 / 创维适配（Android 8.0，API 26）

> Fork of [lesnolie/Tigris](https://github.com/lesnolie/Tigris) v1.9 (`4b56387`), adding API 26
> support and Coocaa / Skyworth adaptations. 中文说明见下。

这个 fork 把 Tigris 回移到 **API 26（Android 8.0）**，并适配**创维 7S710_A3（酷开 OS）**。
其他酷开/创维机型的组件名可能不同，需要按实际固件核对。

已在 7S710_A3（Android 8.0）上验证：主页键、冷启动、输入源切换、24 小时制时钟均正常。

## 为什么需要这个 fork

1. **工具链**：上游 minSdk 28、compileSdk 36、AGP 9.4.1，Android SDK 34 的环境构建不了。
2. **安装限制**：酷开安装器会拒绝声明了 `QUERY_ALL_PACKAGES`、`WRITE_SECURE_SETTINGS`、
   `SYSTEM_ALERT_WINDOW` 的第三方 APK（`INSTALL_FAILED_USER_RESTRICTED`）。三者中至少一个会触发，
   实测同时去掉后可正常安装。
3. **没有输入源**：酷开固件没有注册任何 `TvInputService`，`TvInputManager.tvInputList` 恒为空，
   上游的“输入源”只能显示“无法读取电视的输入源”。
4. **时钟格式**：上游把主页时钟写死为 12 小时制（`h:mm a`），与系统的 24 小时制设置不符。
5. **HOME 不属于本 APK**：酷开桌面不是标准 Android HOME，需要配套的
   [coocaa-home-shim](https://github.com/VincentYe123/coocaa-home-shim) 才能接管主页键和开机首页，
   见下文“设为桌面”。

## 改动

| 文件 | 改动 |
|---|---|
| `build.gradle.kts` | AGP 9.4.1 → 8.5.2，显式引入 Kotlin Android 插件 2.0.21 |
| `app/build.gradle.kts` | compileSdk 36 → 34，minSdk 28 → 26，JVM target 17；recyclerview 1.4.0 → 1.3.2，media3-exoplayer 1.5.1 → 1.4.1（它们要求 compileSdk 35） |
| `AndroidManifest.xml` | 去掉上面三个权限；Tigris 的本地 ADB 功能（应用内关机、卸载应用）随之失效 |
| `ControlCenter.kt` | `BlendMode.PLUS`（API 29）换成 `PorterDuffXfermode(ADD)`，API 26 不再 `NoClassDefFoundError`；增加酷开输入源的状态文案 |
| `TvControls.kt` | 新增 `Coocaa` 回退：没有 TvInputService 时提供“酷开信号源”，打开酷开自带的信号源选择器 |
| `HomeActivity.kt` | 时钟跟随系统 12/24 小时设置 |

共 6 个文件，+51 / −19。

## 构建

需要 JDK 17、Android SDK Platform 34 和 Build-Tools 34。

```bash
export JAVA_HOME=/path/to/jdk-17
bash ./gradlew assembleStandardRelease
# app/build/outputs/apk/standard/release/app-standard-release.apk
```

APK 使用 debug 签名，自用足够；要发布请换用自己的签名。`standard` flavor 声明了标准
`CATEGORY_HOME`，但在酷开固件上真正生效的是下面的 shim。

## 安装

```bash
adb install --no-streaming app-standard-release.apk
```

如果报 `INSTALL_FAILED_USER_RESTRICTED`，检查 APK 是否仍声明了那三个权限；酷开的“安装未知来源”
限制还需要在系统设置里放开。

## 设为桌面

标准的 `cmd package set-home-activity`、`CATEGORY_HOME` 声明在酷开固件上都不生效，酷开用自己的
`persist.service.homepage.pkg/.cls` 指定首页，并且开机时的广告流程会绕过它。可行的非 root 方案是
`coocaa-home-shim`（MIT）：把上述属性指向 shim 的透明 `LaunchActivity`，由它启动 Tigris；
shim 的 `BootWatchService` 在开机后两分钟内盯着前台包名，发现酷开桌面就把系统拉回首页。

需要把 shim 的目标改成 Tigris（上游 release 指向别的桌面），共三处：

```java
// BootWatchService.java
static final String LAUNCHER = "com.leslie.tvhome";

// LaunchActivity.java
if (!start(BootWatchService.LAUNCHER, "com.leslie.tvhome.HomeActivity")) {
    start("com.tianci.movieplatform", "com.coocaa.homepage.vast.HomePageActivity");
}

// BootReceiver.java
.setClassName(BootWatchService.LAUNCHER, "com.leslie.tvhome.BootReceiver")
```

安装 shim 并设置属性：

```bash
adb install coocaa-home-shim-tigris.apk
adb shell 'setprop persist.service.homepage.pkg com.ccos.tv.homepage; \
  setprop persist.service.homepage.cls com.ccos.tv.homepage.LaunchActivity'
```

注意：

- **不要停用原桌面** `com.tianci.movieplatform`，社区有停用后开机卡住的报告；Tigris 被卸载时
  shim 会回退到它。
- shim 的签名与原版不同，替换时必须先 `adb uninstall com.ccos.tv.homepage` 再安装。
- 属性只是指向 shim；卸载 shim 后主页键会没有反应，记得同时把属性指回
  `com.tianci.movieplatform` / `com.coocaa.homepage.vast.HomePageActivity`。

## 输入源

酷开没有 TvInputService，`Coocaa.inputs()` 因此只提供一项“酷开信号源”。点开后是酷开自己的
`SourceDialogActivity`（游戏机 / HDMI2 / 视频 / 射频输入），选中即切源。

这个选择器是 `singleTask` 且 affinity 与原桌面相同，直接启动会复用原桌面的任务栈，按返回会掉进
酷开桌面。这里用 `NEW_TASK | CLEAR_TASK` 让它成为独立任务的根，返回时不会再出现酷开桌面：

```kotlin
Intent("coocaa.intent.action.SOURCE_DIALOG")
    .setComponent(ComponentName("com.tianci.movieplatform",
        "com.ccos.cooui.homepage.tvsource.SourceDialogActivity"))
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
```

在不同固件上组件名可能不同，可用
`dumpsys package com.tianci.movieplatform | grep -i source` 核对。

## 已验证

- 主页键从设置、第三方应用回到 Tigris；冷启动后直接进入 Tigris。
- Tigris 被酷开内存管理器后台强停（`stopped=true`）后，主页键仍能把它拉起。
- 控制中心 → 输入源 → 酷开信号源 → HDMI2，切换成功；再按主页键回到 Tigris。
- 系统设为 24 小时制时，右上角显示 24 小时制时间，与系统时间一致。

## 已知限制

- 酷开内存管理器会在 Tigris 进入后台后强制停止它；按主页键会重新创建任务（可以接受，但任务栈不保留）。
- 打开信号源选择器后，如果按返回时 Tigris 已被后台清理，会落到上一个应用（不会回到酷开桌面）；
  再按主页即可。
- 应用内“本地 ADB”能力（卸载应用、关机）随权限一起被移除，Tigris 设置里的授权提示可以忽略。
- 只在 7S710_A3（Android 8.0）上验证；同代酷开/创维可能通用，需按固件核对组件名。

## 回滚

```bash
adb shell 'setprop persist.service.homepage.pkg com.tianci.movieplatform; \
  setprop persist.service.homepage.cls com.coocaa.homepage.vast.HomePageActivity'
adb uninstall com.ccos.tv.homepage
adb uninstall com.leslie.tvhome
```

## 许可

上游为 MIT License（Copyright © 2026 lesnolie）；本 fork 的改动同样按 MIT 发布。

# 酷开 / 创维适配（Android 8.0，API 26）

> Fork of [lesnolie/Tigris](https://github.com/lesnolie/Tigris) v2.0.2 (`1afd05a`), adding API 26
> support and Coocaa / Skyworth adaptations. 中文说明见下。

这个 fork 把 Tigris **v2.0.2** 回移到 **API 26（Android 8.0）**，并适配**创维 7S710_A3（酷开 OS）**。
其他酷开/创维机型的组件名可能不同，需要按实际固件核对。

已在 7S710_A3（Android 8.0）上验证 v2.0.2：主页、控制中心、输入源、24 小时制时钟、壁纸/手机上传页均正常。

## 为什么需要这个 fork

1. **工具链**：上游 minSdk 28、compileSdk 36、AGP 9.4.1，Android SDK 34 的环境构建不了。
2. **安装限制**：酷开安装器会拒绝声明了 `QUERY_ALL_PACKAGES`、`WRITE_SECURE_SETTINGS`、
   `SYSTEM_ALERT_WINDOW` 的第三方 APK（`INSTALL_FAILED_USER_RESTRICTED`）。三者中至少一个会触发，
   实测同时去掉后可正常安装。
3. **没有输入源**：酷开固件没有注册任何 `TvInputService`，`TvInputManager.tvInputList` 恒为空
   （v2.0.2 已内置 `tv/CoocaaAdapter.kt`，即本 fork 原作者提交的适配）。
4. **时钟格式**：v2.0.2 的 24 小时开关默认关闭、写死 12 小时制；本 fork 让它默认跟随系统设置。
5. **HOME 不属于本 APK**：酷开桌面不是标准 Android HOME，需要配套的
   [coocaa-home-shim](https://github.com/VincentYe123/coocaa-home-shim) 才能接管主页键和开机首页，
   见下文“设为桌面”。

## 改动

在 `1afd05a` 之上合并上游 v2.0.2（tvOS 聚焦动画、开机动画、手机上传壁纸、最近使用优先、
新的设置页等），再做以下 API 26 / 酷开适配：

| 文件 | 改动 |
|---|---|
| `build.gradle.kts` | AGP 9.4.1 → 8.5.2，显式引入 Kotlin Android 插件 2.0.21 |
| `app/build.gradle.kts` | compileSdk 36 → 34，minSdk 28 → 26，JVM target 17；recyclerview 1.4.0 → 1.3.2，media3-exoplayer 1.5.1 → 1.4.1（它们要求 compileSdk 35）；versionCode 9 / `2.0.2-coocaa-api26` |
| `AndroidManifest.xml` | 去掉上面三个权限；Tigris 的本地 ADB 功能（应用内关机、卸载应用）随之失效 |
| `tv/CoocaaAdapter.kt` | 上游已内置（来自本 fork 的 PR）；没有 TvInputService 时提供“酷开信号源”，打开酷开自带的信号源选择器 |
| `tv/AndroidAdapter.kt` | `AudioManager.getStreamMinVolume()` 是 API 28；API 26 回退为 0，音量条不再崩溃 |
| `Prefs.kt` | 时间格式默认取系统 12/24 小时设置；用户在设置里改过后以其为准 |

`ControlCenter.kt` 无需再改：v2.0.2 已用 `PorterDuffXfermode(ADD)` 替代 `BlendMode.PLUS`。

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
`coocaa-home-shim`（MIT）：把上述属性指向 shim 的透明 `LaunchActivity`，由它启动 Tigris。

开机交接用的是 shim v3.8 的做法（对应 `coocaa-home-shim` 仓库的本地改动）：

- 接收 `LOCKED_BOOT_COMPLETED` / `BOOT_COMPLETED` 后启动一个短命的前台服务，读酷开的
  `sky.current.apk`，发现原桌面在前台就通过 `content://com.tianci.provider` 的 `gotoHomePage`
  让系统回首页（Home 就是 shim 的 `LaunchActivity`，它再启动 Tigris）。
- 同时用 `AlarmManager` 在开机后 20/35/50/65/80/100/120/150/180 秒各安排一次一次性的 `PEEK`
  广播：每次只做“看前台包 → 需要就 goto → 立刻退出”，用定时唤醒弥补酷开启动流程晚到的情况。

安装 shim 并设置属性：

```bash
adb install coocaa-home-shim-v3.8.apk
adb shell 'setprop persist.service.homepage.pkg com.ccos.tv.homepage; setprop persist.service.homepage.cls com.ccos.tv.homepage.LaunchActivity'
```

注意：

- **不要停用原桌面** `com.tianci.movieplatform`，社区有停用后开机卡住的报告；Tigris 被卸载时
  shim 会回退到它。
- shim 的签名与原版不同，替换时必须先 `adb uninstall com.ccos.tv.homepage` 再安装。
- 属性只是指向 shim；卸载 shim 后主页键会没有反应，记得同时把属性指回
  `com.tianci.movieplatform` / `com.coocaa.homepage.vast.HomePageActivity`。
- 酷开系统会在某些时机把这两个属性改回它记住的值，改完建议 `getprop` 复查。

## 输入源

酷开没有 TvInputService，`CoocaaAdapter.inputs()` 因此只提供一项“酷开信号源”。点开后是酷开自己的
`SourceDialogActivity`（游戏机 / HDMI2 / 视频 / 射频输入），选中即切源。

这个选择器是 `singleTask` 且 affinity 与原桌面相同，直接启动会复用原桌面的任务栈。上游实现了
同样的 `NEW_TASK | CLEAR_TASK` 启动方式（`AndroidAdapter.start()` 会补上 `NEW_TASK`），
但任务栈最终落到哪里仍取决于固件：本机实测按返回可能落到酷开桌面或上一个应用。

在不同固件上组件名可能不同，可用
`dumpsys package com.tianci.movieplatform | grep -i source` 核对。

## 已验证

在 7S710_A3（Android 8.0）上装 v2.0.2（versionCode 9）：

- 主页键从第三方应用回到 Tigris；Tigris 被酷开内存管理器强停（`stopped=true`）后，主页键仍能拉起。
- 控制中心显示“酷开信号源选择器”，点“输入源”打开酷开信号源选择器，选台正常。
- 系统设为 24 小时制时，右上角显示 24 小时制时间；设置页“时间格式”显示 24 小时。
- 新壁纸页、手机扫码上传页（`http://<电视IP>:8790/...`）均可打开，手机页面返回 200。
- v1.9 的设置（壁纸、应用顺序等）在升级后保留。
- 手动交接（任意应用按主页键）、以及部分冷启动可以进到 Tigris；但冷启动仍然不稳定，见“已知限制”。

## 已知限制

- **冷启动不保证每次都进 Tigris**：酷开内存管理（SkyLowMem）会在 shim 处于后台时 1–2 秒内强制
  停止它；被强停的包（`stopped=true`）会同时错过 `LOCKED_BOOT_COMPLETED` 和 `BOOT_COMPLETED`，
  于是下一次开机没有交接触发，停在原桌面。原桌面或酷开启动流程晚到时也会出现同样结果。
  按一次主页键就能回到 Tigris（系统的 `gotoHomePage` 会直接启动配置的首页）。
- 酷开内存管理器会在 Tigris 进入后台后强制停止它；按主页键会重新创建任务（可以接受，但任务栈不保留）。
- 主页键在**酷开桌面已在前台**时不生效（固件特性）：先打开任意应用再按主页即可回到 Tigris。
- 打开信号源选择器后按返回，可能落到酷开桌面或上一个应用，而不是 Tigris。
- 应用内“本地 ADB”能力（卸载应用、关机）随权限一起被移除；设置里显示“已授权”也可以忽略。
- `versionCode` 与上游不同（本 fork 为 9）；升级时用 `adb install -r` 保留数据。
- 只在 7S710_A3（Android 8.0）上验证；同代酷开/创维可能通用，需按固件核对组件名。

## 回滚

```bash
adb shell 'setprop persist.service.homepage.pkg com.tianci.movieplatform; setprop persist.service.homepage.cls com.coocaa.homepage.vast.HomePageActivity'
adb uninstall com.ccos.tv.homepage
adb uninstall com.leslie.tvhome
```

## 许可

上游为 MIT License（Copyright © 2026 lesnolie）；本 fork 的改动同样按 MIT 发布。

# Tigris

把 Android TV 的桌面换成 tvOS 26 的样子：沉底的 Liquid Glass Dock、Top Shelf 式的顶部内容、毛玻璃控制中心、风景照片背景和相框屏保。

> 十二生肖开源系列第三个项目：虎。Tigris 是拉丁语和希腊语里的"老虎"。

![主屏幕](docs/screenshots/home.jpg)

## 效果图

| 顶部内容（Top Shelf） | 展开全部 App |
|---|---|
| ![顶部内容](docs/screenshots/shelf.jpg) | ![展开](docs/screenshots/grid.jpg) |
| **控制中心** | **相框 / 屏保** |
| ![控制中心](docs/screenshots/cc.jpg) | ![相框](docs/screenshots/frame.jpg) |

## 让 AI Agent 帮你安装

把下面这句话和仓库链接发给 Codex、Claude Code 这类能执行命令的 Agent 即可：

> 按 https://github.com/lesnolie/Tigris 的 AGENTS.md 把 Tigris 装到我的电视上，电视 IP 是 192.168.x.x

Agent 会按 [AGENTS.md](AGENTS.md) 一步步完成：连接电视、安装、设为默认桌面、授权，以及索尼电视的额外配置。过程中电视上会弹出两三次确认框，需要你用遥控器点"允许"。

## 功能

- **主屏幕**：大号细体时钟和日期，5 列 16:9 圆角图标。选中时图标放大、倾斜并带投影。长按图标可以排序、隐藏或卸载 App。
- **Liquid Glass Dock**：第一排 App 放在沉底的玻璃板上，下一排只露出上沿，按下键展开全部 App。玻璃的模糊、边缘折射和彩色高光在 CPU 上按当前背景预先算好（Android 12 没有运行时着色器），所以滚动时不额外占用 GPU。
- **顶部内容**：选中 Dock 里的部分 App 时，上半屏轮播它的内容：
  - 云视听小电视：哔哩哔哩热门视频
  - 斗鱼：正在直播的热门直播间

  内容来自这两个服务公开的网页接口，因为国内 App 基本不往 Android TV 的系统频道接口里写数据。
- **控制中心**（遥控器返回键或右上角按钮）：毛玻璃面板，选中的控件会照亮周围的玻璃。包括待机、关机、重启、释放内存、相框、设置，以及亮度、音量和 HDMI 输入源。
- **背景**：必应每日图片，每天自动下载，可设置每 15 分钟到 1 天换一张。
- **相框 / 屏保**：全屏轮播照片，画面缓慢平移缩放，时间文字会定时挪动位置，防止 OLED 烙印。可以手动打开，也可以设为系统屏保。
- **内存清理**：定时清理，电视待机时也会清理一次，保留名单可在设置中修改。

## 哪些功能所有电视都能用

| 功能 | 任意 Android TV（Android 9+） | 需要本机调试授权 | 仅索尼 BRAVIA |
|---|:-:|:-:|:-:|
| 主屏幕、Dock、玻璃效果、投影 | ✅ | | |
| 顶部内容（B站 / 斗鱼） | ✅ 需要装对应的 TV 版 App | | |
| 风景照片背景、相框 | ✅ | | |
| 控制中心界面、时钟 | ✅ | | |
| 释放内存 | ✅ 只能清理缓存 | ✅ 彻底结束后台 App | |
| 待机、关机、重启 | | ✅ | |
| 把相框设为系统屏保、空闲时长 | | ✅ | |
| 亮度、音量滑条 | | | ✅ |
| HDMI 输入源切换 | | | ✅ |

- **本机调试授权**：App 会连接电视自己的 adb（`127.0.0.1:5555`，基于 [dadb](https://github.com/mobile-dev-inc/dadb)）来执行系统命令。前提是电视在开发者选项里打开了"网络调试 / ADB 调试"。首次使用时电视会弹出"允许调试吗？"，勾选"始终允许"即可。这个机制在各品牌电视上都通用。
- **仅索尼**：亮度、音量和输入源使用索尼 BRAVIA 的 IP 控制接口（`http://127.0.0.1/sony/`）。在其他品牌的电视上，这几项会显示为灰色。

### 想兼容其他品牌电视

这三项都集中在 [`Bravia.kt`](app/src/main/java/com/leslie/tvhome/Bravia.kt) 里，可以按下面的思路替换：

- **音量**：改用 Android 标准的 `AudioManager.setStreamVolume(STREAM_MUSIC, …)`，所有电视都能用。不过电视用 ARC/eARC 接功放时，调的可能不是功放的音量。
- **输入源**：改用 `TvInputManager.getTvInputList()` 列出 HDMI 输入，切换时用 `TvContract.buildChannelUriForPassthroughInput(inputId)` 打开系统的直播电视 App。大多数带 HDMI 的 Android TV 都支持这种方式。
- **亮度（背光）**：Android 没有标准接口，各品牌（TCL、海信、小米等）都是私有的。可以对接厂商接口，或者在非索尼电视上隐藏这一项。

欢迎提交其他品牌的适配 PR。

## 手动构建和安装

需要 JDK 17 和 Android SDK（compileSdk 36）。也可以直接下载 [Releases](https://github.com/lesnolie/Tigris/releases/latest) 里的 APK。

```sh
./gradlew assembleRelease
adb connect <电视IP>:5555
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell cmd package set-home-activity com.leslie.tvhome/.HomeActivity   # 设为默认桌面
```

索尼电视要用亮度、音量和输入源，还需要注册一次 BRAVIA 客户端：

```sh
BODY='{"method":"actRegister","id":1,"version":"1.0","params":[{"clientid":"tvhome:1","nickname":"TVHome","level":"private"},[{"value":"yes","function":"WOL"}]]}'
curl -s http://<电视IP>/sony/accessControl -d "$BODY"              # 电视屏幕上会显示 4 位 PIN
curl -s -u ":<PIN>" http://<电视IP>/sony/accessControl -d "$BODY"  # 用这个 PIN 完成注册
```

注册之后 App 会自动续期授权。客户端 ID 是固定的，只有你家局域网里的设备能用它，外网访问不到。

release 包使用 debug 签名，适合自己侧载。

## 许可

代码采用 [MIT](LICENSE) 许可证。第三方字体、图标和依赖的许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

本项目与 Apple、Sony、哔哩哔哩、斗鱼没有关联。截图中的封面、照片版权归各自作者所有。

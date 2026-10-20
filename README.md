# TVHome

仿 tvOS 26 的 Android TV 桌面启动器。最初为一台索尼 BRAVIA（Android TV 12）做的，其他 Android TV 也能用，只是部分功能依赖索尼电视。

## 功能

- **主屏幕**：大号细体时钟和日期，5 列 16:9 圆角图标，选中时放大、倾斜并带投影；长按可以排序、隐藏或卸载 App。
- **Liquid Glass Dock**：第一排 App 放在一块沉底的玻璃板上，下一排只露出上沿。玻璃的模糊、边缘折射和彩色高光在 CPU 上按当前背景预先算好（Android 12 没有运行时着色器），所以滚动时不额外耗 GPU。
- **顶部内容（类似 tvOS 的 Top Shelf）**：选中 Dock 里的部分 App 时，上半屏轮播它的内容。目前支持：
  - 云视听小电视：哔哩哔哩热门视频
  - 斗鱼：正在直播的热门直播间

  内容来自这两个服务公开的网页接口。Android TV 自带的频道接口在国内 App 上基本没有数据。
- **控制中心**（返回键或右上角按钮）：毛玻璃面板，选中的控件会照亮周围的玻璃。包括：
  - 待机、关机、重启、释放内存、相框、设置
  - 亮度和音量滑条
  - HDMI 输入源切换
- **背景**：必应每日图片，每天自动下载，可设置每 15 分钟到 1 天更换一次；也可以改用几种渐变主题。
- **相框 / 屏保**：全屏轮播照片，画面缓慢平移缩放，时间文字会定时挪动位置，防止 OLED 烙印。可以手动打开，也可以设为系统屏保（`PhotoDream`）。
- **内存清理**：定时清理，按电源键待机时也会清理一次，保留名单可在设置中修改。

## 依赖本机权限的功能

关机、待机、彻底清理内存，以及把相框设为系统屏保，需要用到 adb shell 权限。App 会连接电视自己的 adbd（`127.0.0.1:5555`，基于 [dadb](https://github.com/mobile-dev-inc/dadb)）。首次使用时，电视会弹出"允许调试吗？"，请勾选"始终允许"。为此需要先在电视上打开"网络调试"。

亮度、音量和输入源通过索尼 BRAVIA 的 IP 控制接口（`http://127.0.0.1/sony/`）实现。读取不需要授权；修改需要客户端 `tvhome:1` 先完成一次注册。App 里还没有输入 PIN 的界面，可以在同一局域网的电脑上完成注册：

```sh
BODY='{"method":"actRegister","id":1,"version":"1.0","params":[{"clientid":"tvhome:1","nickname":"TVHome","level":"private"},[{"value":"yes","function":"WOL"}]]}'
curl -s http://<电视IP>/sony/accessControl -d "$BODY"              # 电视屏幕上会显示 4 位 PIN
curl -s -u ":<PIN>" http://<电视IP>/sony/accessControl -d "$BODY"  # 用这个 PIN 完成注册
```

注册之后，App 会自动续期授权。注意：客户端 ID 是固定的，同一局域网内知道这个 ID 的设备也能在不输入 PIN 的情况下拿到授权，从而调电视的音量、亮度和输入源。请只在可信的家庭网络中使用。非索尼电视上这几项会显示为不可用。

## 构建和安装

需要 JDK 17 和 Android SDK（compileSdk 36）：

```sh
./gradlew assembleRelease
adb connect <电视IP>:5555
adb install -r app/build/outputs/apk/release/app-release.apk
# 设为默认桌面
adb shell cmd package set-home-activity com.leslie.tvhome/.HomeActivity
```

release 包使用 debug 签名，仅适合自己侧载。

## 许可

代码采用 [MIT](LICENSE) 许可证。第三方字体、图标和依赖的许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

本项目与 Apple、Sony、哔哩哔哩、斗鱼没有关联。各公司名称和商标归各自所有者所有。

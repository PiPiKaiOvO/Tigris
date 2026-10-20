# Installing Tigris — instructions for AI agents

You are installing Tigris, an Android TV launcher, onto the user's TV from their computer. Follow these steps in order. Talk to the user in their language (most users write Chinese). The TV is someone's home device: whenever a step below says to ask, ask first.

## Hard rules

- Never run `svc power shutdown`, `svc power reboot`, `reboot` or `input keyevent KEYCODE_POWER` / `KEYCODE_SLEEP` without asking. A powered-off TV can only be turned back on with its remote.
- Never `pm uninstall` a system app or the TV's own launcher. If the stock launcher keeps taking over, disable it only with the user's consent (step 5), and tell them how to undo it.
- Before you send `KEYCODE_DPAD_CENTER` / `ENTER` on the TV, check what is focused (step 6). Many Tigris buttons power the TV off or put it to sleep.
- Do not change other TV settings (animation scales, app permissions, background restrictions) unless the user asks.

## 0. What you need

- A computer on the same local network as the TV, with `adb` installed (Android SDK Platform-Tools). Check with `adb version`. If it is missing, install it: Windows `winget install Google.PlatformTools`, macOS `brew install android-platform-tools`, Debian/Ubuntu `apt install adb`.
- The TV's IP address. Ask the user. It is under Settings → Network & Internet → (connected network) on most Android TVs.
- Network debugging enabled on the TV. If `adb connect` fails in step 1, walk the user through this:
  1. Open Settings → Device Preferences → About, and press OK on "Build" seven times to unlock Developer options.
  2. Open Settings → Device Preferences → Developer options, and turn on "USB debugging". Also turn on "Network debugging" / "ADB over network" if the menu has it.

## 1. Connect

```sh
adb connect <TV_IP>:5555
adb -s <TV_IP>:5555 get-state     # should print "device"
```

If it prints `unauthorized`, the TV is showing "Allow USB debugging?". Ask the user to tick "Always allow from this computer" and press Allow, then retry. Use `-s <TV_IP>:5555` on every later command.

## 2. Check the TV

```sh
adb -s <TV_IP>:5555 shell getprop ro.build.version.sdk          # must be >= 28 (Android 9)
adb -s <TV_IP>:5555 shell getprop ro.product.manufacturer       # "Sony" adds the brightness slider (step 7)
adb -s <TV_IP>:5555 shell getprop ro.product.model
adb -s <TV_IP>:5555 shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s <TV_IP>:5555 shell settings get secure screensaver_components
```

Note the current home activity (the stock launcher) and the current screensaver; you need them to undo the install. Tell the user which features they will get (see the compatibility table in README.md). On TVs other than Sony, volume and HDMI switching use standard Android APIs, and the brightness slider is hidden. Section 9 covers adapting the app when something doesn't work.

## 3. Install

Download the latest release APK and install it:

```sh
curl -L -o Tigris.apk https://github.com/lesnolie/Tigris/releases/latest/download/Tigris.apk
adb -s <TV_IP>:5555 install -r Tigris.apk
```

If GitHub is unreachable, build from source instead: clone the repo, then `./gradlew assembleRelease` (needs JDK 17 and an Android SDK; set `sdk.dir` in `local.properties`). The APK is `app/build/outputs/apk/release/app-release.apk`.

## 4. Make it the home screen

```sh
adb -s <TV_IP>:5555 shell cmd package set-home-activity com.leslie.tvhome/.HomeActivity
adb -s <TV_IP>:5555 shell input keyevent KEYCODE_HOME
```

## 5. Verify

Take a screenshot and look at it:

```sh
adb -s <TV_IP>:5555 exec-out screencap -p > tv.png
```

You should see a large clock top-left, a row of app tiles on a glass panel near the bottom, and two round icons top-right. If the stock launcher appears instead, run step 4 again and press Home once more. If it still comes back, the vendor launcher is overriding the default. Ask the user whether to disable it, and only then:

```sh
adb -s <TV_IP>:5555 shell pm disable-user --user 0 <stock launcher package>
# undo: adb -s <TV_IP>:5555 shell pm enable <stock launcher package>
```

On first start the app downloads background photos (needs internet). The first minute may show a gradient.

## 6. Local debugging authorization (power, sleep, deep memory cleanup)

On first start Tigris asks to enable these features. When the user presses "开始", the TV shows "Allow debugging?" again, this time from the TV to itself. The user must tick "Always allow" and press Allow. Check the screen with `screencap` and guide them. You can verify afterwards: the Control Center (Back key on the home screen) no longer shows a "本机调试未授权" line.

If you need to press buttons for the user, first find the focused element:

```sh
adb -s <TV_IP>:5555 shell uiautomator dump /sdcard/u.xml && adb -s <TV_IP>:5555 shell cat /sdcard/u.xml
```

Look for `focused="true"` and its `text` / `bounds` before sending `KEYCODE_DPAD_CENTER`.

## 7. Sony BRAVIA only: brightness, plus TV-side volume and HDMI

Skip this step if the manufacturer is not Sony. The TV's IP control API must accept a registration for client `tvhome:1`:

```sh
BODY='{"method":"actRegister","id":1,"version":"1.0","params":[{"clientid":"tvhome:1","nickname":"TVHome","level":"private"},[{"value":"yes","function":"WOL"}]]}'
curl -s http://<TV_IP>/sony/accessControl -d "$BODY"
```

The TV now shows a 4-digit PIN for about a minute. Ask the user to read it out, or read it from a `screencap`. Then:

```sh
curl -s -u ":<PIN>" http://<TV_IP>/sony/accessControl -d "$BODY"   # {"result":[]} means success
```

If the first call returns an error or nothing, ask the user to enable Settings → Network & Internet → Remote device settings → "Control remotely" (the menu name varies by model), then retry. To check: open the Control Center. The brightness slider is shown and the input row says "当前：…".

## 8. Optional settings

Tell the user these exist; change them only if asked. All of them are in the Control Center → 设置 (gear), or on the "主屏幕设置" page:

- Photo frame as the system screensaver (电视空闲时显示相框) and the idle time.
- Background photo change interval, or switch to a gradient theme.
- Apps kept alive by memory cleanup.

## 9. Adapting Tigris to a TV that isn't fully supported

Do this only when the user asks for it, or agrees after you report what doesn't work. Typical gaps on TVs that aren't Sony:

- The brightness slider is missing. Android has no standard backlight API.
- The volume slider moves but the sound doesn't change, for example when audio goes to an amplifier over ARC.
- The input row says "无法读取电视的输入源", or picking an HDMI input does nothing.

All three live in [`TvControls.kt`](app/src/main/java/com/leslie/tvhome/TvControls.kt). It tries Sony's API first, then standard Android. A new brand is one more branch there, usually gated on `Build.MANUFACTURER`. Don't change the Sony or standard paths.

### 9.1 Set up a build

- Clone https://github.com/lesnolie/Tigris. You need JDK 17+ (`java -version`) and an Android SDK with `platforms;android-36` and `build-tools` (install them with `sdkmanager`). Put `sdk.dir=<path>` in `local.properties`. Build with `./gradlew assembleRelease`.
- Your build is signed with this computer's debug key, which differs from the Release APK. `adb install -r` will fail with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Ask the user before you `adb uninstall com.leslie.tvhome` first: it resets the app's settings, and afterwards you must redo step 4 and the user must allow local debugging again (step 6).

### 9.2 Find what the TV offers

Work from adb. Before you write anything, note each value you read; restore the original values when you are done.

- **Volume**: `adb shell cmd media_session volume --stream 3 --get` shows what the app's slider controls. Change it with `--set <n>` and ask the user whether the sound changed. If it didn't, look in `dumpsys audio` for the active output device and whether HDMI-CEC volume control is in use (`dumpsys hdmi_control`).
- **HDMI inputs**: `adb shell dumpsys tv_input` lists the inputs with their ids and states; the HDMI ones usually have `HDMI` or `HW` in the id. Test a switch the way the app does it:
  `adb shell am start -a android.intent.action.VIEW -d "content://android.media.tv/passthrough/<url-encoded input id>"`
  If nothing happens, run `adb logcat -c`, ask the user to switch input with the remote, then read `adb logcat -d | grep -iE "START u0|tune|input"` to find the vendor's player activity and the intent it uses.
- **Brightness / backlight**: take a snapshot with `adb shell settings list system; settings list global; settings list secure`. Ask the user to change the backlight in the TV's picture settings, then take the snapshot again and diff the two. A key that changed (names like `backlight`, `picture_backlight`) is a candidate. Test it with `settings put`, ask the user whether the screen changed, then put the original value back. Also check logcat during the change, and `adb shell ss -ltn` for vendor control services on localhost, like Sony's port 80.

### 9.3 Implement and verify

- Add the branch to `TvControls.kt`. `brightness()` returning a non-null `Bravia.Level(value, min, max)` makes the slider appear; `setBrightness` is then called with values in that range. All of these run on a background thread, so blocking I/O is fine.
- The app has no permission to write settings. Pick one of these:
  - Grant `WRITE_SECURE_SETTINGS` with `adb shell pm grant com.leslie.tvhome android.permission.WRITE_SECURE_SETTINGS`, after adding it to the manifest. Note that the grant is lost on uninstall.
  - Grant `WRITE_SETTINGS` (system table) with `adb shell appops set com.leslie.tvhome WRITE_SETTINGS allow`.
  - Run the command through the app's own local adb, `LocalAdb.shell(ctx, "settings put …")` in `Cleaner.kt`. This works once the user has allowed local debugging.
- Build, install, open the Control Center (Back on the home screen), and `screencap`. Move to the slider with D-pad keys, checking the focused element first (step 6). Then read the value back the same way as in 9.2 to confirm the change.

### 9.4 Finish

Tell the user what now works and what still doesn't. Their adapted build stays on their TV. If they want to share it, offer to commit the change on their own fork and open a pull request to `lesnolie/Tigris` with their GitHub account, naming the TV model and Android version. Never push to the upstream repository directly.

## Uninstall

```sh
adb -s <TV_IP>:5555 uninstall com.leslie.tvhome
```

The TV falls back to its stock launcher. If you disabled it in step 5, re-enable it first. If the photo frame was made the screensaver, put the original back (the value noted in step 2):

```sh
adb -s <TV_IP>:5555 shell settings put secure screensaver_components <original value>
```

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
adb -s <TV_IP>:5555 shell getprop ro.product.manufacturer       # "Sony" enables brightness/volume/HDMI
adb -s <TV_IP>:5555 shell getprop ro.product.model
adb -s <TV_IP>:5555 shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s <TV_IP>:5555 shell settings get secure screensaver_components
```

Note the current home activity (the stock launcher) and the current screensaver; you need them to undo the install. Tell the user which features they will get (see the compatibility table in README.md): non-Sony TVs get everything except the brightness, volume and HDMI controls.

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

## 7. Sony BRAVIA only: brightness, volume and HDMI

Skip this step if the manufacturer is not Sony. The TV's IP control API must accept a registration for client `tvhome:1`:

```sh
BODY='{"method":"actRegister","id":1,"version":"1.0","params":[{"clientid":"tvhome:1","nickname":"TVHome","level":"private"},[{"value":"yes","function":"WOL"}]]}'
curl -s http://<TV_IP>/sony/accessControl -d "$BODY"
```

The TV now shows a 4-digit PIN for about a minute. Ask the user to read it out, or read it from a `screencap`. Then:

```sh
curl -s -u ":<PIN>" http://<TV_IP>/sony/accessControl -d "$BODY"   # {"result":[]} means success
```

If the first call returns an error or nothing, ask the user to enable Settings → Network & Internet → Remote device settings → "Control remotely" (the menu name varies by model), then retry. To check: open the Control Center. The brightness and volume sliders show numbers instead of "—".

## 8. Optional settings

Tell the user these exist; change them only if asked. All of them are in the Control Center → 设置 (gear), or on the "主屏幕设置" page:

- Photo frame as the system screensaver (电视空闲时显示相框) and the idle time.
- Background photo change interval, or switch to a gradient theme.
- Apps kept alive by memory cleanup.

## Uninstall

```sh
adb -s <TV_IP>:5555 uninstall com.leslie.tvhome
```

The TV falls back to its stock launcher. If you disabled it in step 5, re-enable it first. If the photo frame was made the screensaver, put the original back (the value noted in step 2):

```sh
adb -s <TV_IP>:5555 shell settings put secure screensaver_components <original value>
```

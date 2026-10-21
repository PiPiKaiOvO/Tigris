/* Locale-independent values: brand facts, asset paths and shell commands.
   Command snippets deliberately avoid shell variables ($P, $VAR) so they can
   be pasted into zsh, bash, Windows PowerShell or CMD unchanged. The only
   Windows differences are the curl.exe alias and `python` instead of
   `python3`, called out next to each snippet. */

export const brand = {
  name: "TIGRIS",
  product: "Tigris",
  tagline: "Android TV",
  site: "https://tigris-tv.pages.dev",
  repo: "https://github.com/lesnolie/Tigris",
  releases: "https://github.com/lesnolie/Tigris/releases",
  apk: "https://github.com/lesnolie/Tigris/releases/latest/download/Tigris.apk",
  apkGuard:
    "https://github.com/lesnolie/Tigris/releases/latest/download/Tigris-guard.apk",
  agents: "https://github.com/lesnolie/Tigris/blob/main/AGENTS.md",
  readme: "https://github.com/lesnolie/Tigris/blob/main/README.md",
  issues: "https://github.com/lesnolie/Tigris/issues",
  license: "https://github.com/lesnolie/Tigris/blob/main/LICENSE",
  notices:
    "https://github.com/lesnolie/Tigris/blob/main/THIRD_PARTY_NOTICES.md",
  tcast:
    "https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/tcast-push.py",
  pkg: "com.leslie.tvhome",
  tvIp: "192.168.x.x",
} as const;

/* Authentic captures optimised from TVHome/docs/screenshots by
   scripts/build-assets.py. Never replace these with generated images. */
export const shots = {
  home: { src: "/img/home-1280.webp", small: "/img/home-640.webp" },
  shelf: { src: "/img/shelf-1280.webp", small: "/img/shelf-640.webp" },
  grid: { src: "/img/grid-1280.webp", small: "/img/grid-640.webp" },
  cc: { src: "/img/cc-1280.webp", small: "/img/cc-640.webp" },
  frame: { src: "/img/frame-1280.webp", small: "/img/frame-640.webp" },
} as const;

export const assets = {
  beforeIllustration: "/img/before-illustration.svg",
  og: "/img/og.jpg",
  favicon: "/img/favicon.svg",
} as const;

const T = brand.tvIp;
const P = brand.pkg;
const A = `adb -s ${T}:5555`;

export const cmds = {
  snapshotBeforeInstall: `${A} shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME
${A} shell settings get secure screensaver_components
${A} shell settings get secure enabled_accessibility_services`,

  connect: `adb connect ${T}:5555
${A} get-state`,

  installStandard: `curl -L -o Tigris.apk ${brand.apk}
${A} install -r Tigris.apk`,

  setHomeAndGrant: `${A} shell cmd package set-home-activity ${P}/.HomeActivity
${A} shell input keyevent KEYCODE_HOME
${A} shell pm grant ${P} android.permission.WRITE_SECURE_SETTINGS
${A} shell cmd notification allow_listener ${P}/${P}.RevivalListener
${A} shell appops set ${P} SYSTEM_ALERT_WINDOW allow`,

  grantOnly: `${A} shell pm grant ${P} android.permission.WRITE_SECURE_SETTINGS
${A} shell cmd notification allow_listener ${P}/${P}.RevivalListener
${A} shell appops set ${P} SYSTEM_ALERT_WINDOW allow`,

  tclPush: `curl -L -o Tigris-guard.apk ${brand.apkGuard}
curl -L -o tcast-push.py ${brand.tcast}
python3 tcast-push.py ${T} Tigris-guard.apk`,

  tclGrants: `${A} shell pm grant ${P} android.permission.WRITE_SECURE_SETTINGS
${A} shell cmd notification allow_listener ${P}/${P}.RevivalListener
${A} shell appops set ${P} SYSTEM_ALERT_WINDOW allow
${A} shell appops set ${P} APP_AUTO_START allow
${A} shell appops set ${P} APP_ASSOC_START allow
${A} shell am broadcast -a ${P}.CONFIG -n ${P}/.ConfigReceiver --ez guard true`,

  tclCheck: `${A} shell settings get secure enabled_accessibility_services
${A} shell input keyevent KEYCODE_HOME
${A} shell dumpsys activity activities | grep -m1 ResumedActivity`,

  packagePath: `${A} shell pm path ${P}`,

  xiaomiSetup: `${A} shell cmd package set-home-activity ${P}/.HomeActivity
${A} shell pm grant ${P} android.permission.WRITE_SECURE_SETTINGS
${A} shell cmd notification allow_listener ${P}/${P}.RevivalListener
${A} shell appops set ${P} SYSTEM_ALERT_WINDOW allow`,

  xiaomiGuard: `${A} shell am broadcast -a ${P}.CONFIG -n ${P}/.ConfigReceiver --ez guard true --ez keep_adb true`,

  stockInstall: `curl -L -o Tigris.apk ${brand.apk}
${A} install -r Tigris.apk
${A} shell cmd package set-home-activity ${P}/.HomeActivity
${A} shell input keyevent KEYCODE_HOME`,

  verify: `${A} exec-out screencap -p > tv.png`,

  /* Uninstall, in order: guard off, restore the recorded screensaver, remove. */
  uninstall: `${A} shell am broadcast -a ${P}.CONFIG -n ${P}/.ConfigReceiver --ez guard false --ez keep_adb false`,

  restoreScreensaver: `${A} shell settings put secure screensaver_components <ORIGINAL_VALUE>`,

  uninstallPackage: `${A} uninstall ${P}`,

  reenableVendor: `${A} shell pm enable <VENDOR_LAUNCHER_PACKAGE>`,

  /* Informed, reversible last resort — never on Xiaomi / Redmi, which boot to
     a black screen without their launcher. */
  disableVendor: `${A} shell pm disable-user --user 0 <VENDOR_LAUNCHER_PACKAGE>`,
} as const;

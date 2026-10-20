package com.leslie.tvhome

import android.content.Context
import android.content.SharedPreferences

/** All user settings in one place. */
class Prefs(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("tvhome", Context.MODE_PRIVATE)

    var order: List<String>
        get() = sp.getString("order", "")!!.split(',').filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("order", v.joinToString(",")).apply()

    var hidden: Set<String>
        get() = sp.getStringSet("hidden", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("hidden", v).apply()

    /** Packages that memory cleanup never stops (casting receivers etc.). */
    var keep: Set<String>
        get() = sp.getStringSet("keep", DEFAULT_KEEP)!!.toSet()
        set(v) = sp.edit().putStringSet("keep", v).apply()

    var cleanOnStandby: Boolean
        get() = sp.getBoolean("clean_on_standby", true)
        set(v) = sp.edit().putBoolean("clean_on_standby", v).apply()

    /** Hours between automatic cleanups; 0 = off. */
    var cleanIntervalHours: Int
        get() = sp.getInt("clean_interval", 4)
        set(v) = sp.edit().putInt("clean_interval", v).apply()

    var shutdownOnPowerKey: Boolean
        get() = sp.getBoolean("shutdown_on_power", false)
        set(v) = sp.edit().putBoolean("shutdown_on_power", v).apply()

    /** One of [Backgrounds.THEMES] keys, or "daily" for rotating Bing photos. */
    var background: String
        get() = sp.getString("background", "daily")!!
        set(v) = sp.edit().putString("background", v).apply()

    /** Minutes between background photo changes. */
    var wallMinutes: Int
        get() = sp.getInt("wall_minutes", 30)
        set(v) = sp.edit().putInt("wall_minutes", v).apply()

    /** Seconds each photo stays up in the photo frame. */
    var frameSeconds: Int
        get() = sp.getInt("frame_seconds", 30)
        set(v) = sp.edit().putInt("frame_seconds", v).apply()

    /** The photo frame is the system screensaver (instead of Sony's). */
    var frameScreensaver: Boolean
        get() = sp.getBoolean("frame_screensaver", false)
        set(v) = sp.edit().putBoolean("frame_screensaver", v).apply()

    var adbAuthorized: Boolean
        get() = sp.getBoolean("adb_ok", false)
        set(v) = sp.edit().putBoolean("adb_ok", v).apply()

    var lastClean: String
        get() = sp.getString("last_clean", "")!!
        set(v) = sp.edit().putString("last_clean", v).apply()

    companion object {
        val DEFAULT_KEEP = setOf(
            "com.dangbei.screencast",
            "com.xiaodianshi.tv.yst",
            "dev.vodik7.atvtoolsserver",
        )

        /** Initial grid order on first run; everything else follows alphabetically. */
        val DEFAULT_ORDER = listOf(
            "com.xiaodianshi.tv.yst",
            "com.ktcp.video",
            "com.starcor.mango",
            "com.liskovsoft.smarttubetv.beta",
            "com.douyu.xl.douyutv",
            "com.huya.nftv",
            "com.fongmi.android.tv",
            "com.github.tvbox.osc.tk",
            "org.xbmc.kodi",
            "com.quark.yun.tv",
            "com.dangbei.screencast",
            "tv.twitch.android.app",
            "com.limelight",
            "com.limelight.qiin",
            "com.android.tv.settings",
        )
    }
}

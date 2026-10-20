package com.leslie.tvhome

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.util.Log

/**
 * Brightness, volume and HDMI inputs for the Control Center. Sony's BRAVIA
 * API is used when the TV answers it; every other TV falls back to standard
 * Android APIs. To support another brand's own API, add it here.
 *
 * TCL / FFalcon China firmware (tcl_mt5879_cn and relatives) is handled by
 * [Tcl]: no TvInputFramework inputs are registered, the backlight only lives
 * in TCL's private PQ stack, and TCL's own settings app is the only thing
 * that answers a settings intent.
 */
class TvControls(private val ctx: Context) {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private var braviaVolume = false

    /** The backlight has no standard Android API, so this is null (and the slider hidden) off Sony TVs. */
    fun brightness(): Bravia.Level? = Bravia.brightness() ?: Tcl.brightness(ctx)

    fun setBrightness(v: Int) {
        if (Bravia.brightness() != null) { Bravia.setBrightness(v); return }
        Tcl.setBrightness(ctx)
    }

    fun volume(): Bravia.Level? {
        Bravia.volume()?.let { braviaVolume = true; return it }
        braviaVolume = false
        val s = AudioManager.STREAM_MUSIC
        return Bravia.Level(audio.getStreamVolume(s), audio.getStreamMinVolume(s), audio.getStreamMaxVolume(s))
    }

    fun setVolume(target: String, v: Int): Boolean {
        if (braviaVolume) return Bravia.setVolume(target, v)
        // TCL only pushes the new value to the hardware while the system volume bar is shown,
        // so FLAG_SHOW_UI is required there too; it is harmless elsewhere.
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, AudioManager.FLAG_SHOW_UI)
        return true
    }

    fun inputs(): List<Bravia.Input> {
        val sony = Bravia.inputs()
        if (sony.isNotEmpty()) return sony
        val android = androidInputs()
        return if (android.isNotEmpty()) android else Tcl.inputs(ctx)
    }

    /** Uri of the HDMI input being shown; only Sony reports it. */
    fun currentInput(): String? = Bravia.currentInput()

    fun setInput(i: Bravia.Input) {
        if (i.uri.startsWith(Tcl.URI_PREFIX)) { Tcl.setInput(ctx); return }
        if (!i.uri.startsWith("content:")) { Bravia.setInput(i.uri); return }
        // Android's way to show an HDMI input: open its passthrough channel in the TV's own player.
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(i.uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w("TvControls", "cannot open ${i.uri}", e)
        }
    }

    /** The TV maker's own settings app where the standard intent is not answered (TCL). */
    fun settingsIntent(): Intent = Tcl.settingsIntent(ctx) ?: Intent(android.provider.Settings.ACTION_SETTINGS)

    /** HDMI ports from the TV input framework; devices behind a port (CEC) are left out. */
    private fun androidInputs(): List<Bravia.Input> {
        val tim = ctx.getSystemService(TvInputManager::class.java) ?: return emptyList()
        return try {
            tim.tvInputList.filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null }.map {
                val title = it.loadLabel(ctx).toString()
                Bravia.Input(
                    TvContract.buildChannelUriForPassthroughInput(it.id).toString(),
                    title,
                    it.loadCustomLabel(ctx)?.toString()?.takeIf { l -> l != title }.orEmpty(),
                    tim.getInputState(it.id) == TvInputManager.INPUT_STATE_CONNECTED,
                )
            }.sortedBy { it.title }
        } catch (e: Exception) {
            Log.w("TvControls", "inputs failed", e)
            emptyList()
        }
    }
}

/**
 * TCL / FFalcon China firmware.
 *
 * - Inputs: the firmware registers no TvInputService at all (`dumpsys tv_input` has an empty
 *   inputMap), so `TvInputManager.tvInputList` is empty and the standard passthrough URI has no
 *   handler. LiveTV owns the HDMI ports through its own Unified Source stack, so switching means
 *   opening LiveTV's source picker.
 * - Brightness: TCL ignores the standard `screen_brightness` key and has no sysfs backlight node;
 *   the backlight lives behind `com.tcl.tv.pq` / TPictureManager. The reachable path is TCL's own
 *   settings panel, which opens on its 图像 page where the brightness slider sits.
 * - Settings: nothing answers `android.settings.SETTINGS`; TCL's settings app opens with ACTION_MAIN.
 */
private object Tcl {
    const val URI_PREFIX = "tcl-input://"
    private const val SETTINGS_PKG = "com.tcl.settings"
    private const val LIVE_TV = "com.tcl.tv"

    private fun installed(ctx: Context, pkg: String) =
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    /** Non-null makes the brightness slider appear; the slider then only opens TCL's own panel. */
    fun brightness(ctx: Context): Bravia.Level? =
        if (installed(ctx, SETTINGS_PKG)) Bravia.Level(50, 0, 100) else null

    fun setBrightness(ctx: Context) {
        start(ctx, Intent(Intent.ACTION_MAIN).setComponent(ComponentName(SETTINGS_PKG, "$SETTINGS_PKG.ui.MainActivity")))
            ?: start(ctx, Intent(SETTINGS_PKG + ".SHOW_WINDOW").setPackage(SETTINGS_PKG))
    }

    // LiveTV's source list: HDMI1-4 map to hardware types HDMI 1-4 in TvInputHardwareManager.
    private val PORT_NAMES = listOf("HDMI1 (ARC/eARC)", "HDMI2", "HDMI3", "HDMI4")

    fun inputs(ctx: Context): List<Bravia.Input> =
        if (installed(ctx, LIVE_TV)) PORT_NAMES.map { Bravia.Input(URI_PREFIX + it, it, "", false) } else emptyList()

    /** Opens LiveTV, then its own source picker (TV / HDMI1(ARC/eARC) / HDMI2-4 / AV / 投屏). */
    fun setInput(ctx: Context) {
        start(ctx, Intent(Intent.ACTION_VIEW).setComponent(ComponentName(LIVE_TV, "$LIVE_TV.TVActivity")))
    }

    fun settingsIntent(ctx: Context): Intent? =
        if (installed(ctx, SETTINGS_PKG))
            Intent(Intent.ACTION_MAIN).setComponent(ComponentName(SETTINGS_PKG, "$SETTINGS_PKG.ui.MainActivity"))
        else null

    private fun start(ctx: Context, i: Intent): Boolean = runCatching {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrElse { Log.w("TvControls", "TCL start failed for $i", it); false }
}

package com.leslie.tvhome

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
 */
class TvControls(private val ctx: Context) {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private var braviaVolume = false

    /** The backlight has no standard Android API, so this is null (and the slider hidden) off Sony TVs. */
    fun brightness(): Bravia.Level? = Bravia.brightness()

    fun setBrightness(v: Int) = Bravia.setBrightness(v)

    fun volume(): Bravia.Level? {
        Bravia.volume()?.let { braviaVolume = true; return it }
        braviaVolume = false
        val s = AudioManager.STREAM_MUSIC
        return Bravia.Level(audio.getStreamVolume(s), audio.getStreamMinVolume(s), audio.getStreamMaxVolume(s))
    }

    fun setVolume(target: String, v: Int): Boolean {
        if (braviaVolume) return Bravia.setVolume(target, v)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, AudioManager.FLAG_SHOW_UI)
        return true
    }

    fun inputs(): List<Bravia.Input> = Bravia.inputs().ifEmpty { androidInputs() }

    /** Uri of the HDMI input being shown; only Sony reports it. */
    fun currentInput(): String? = Bravia.currentInput()

    fun setInput(i: Bravia.Input) {
        if (!i.uri.startsWith("content:")) { Bravia.setInput(i.uri); return }
        // Android's way to show an HDMI input: open its passthrough channel in the TV's own player.
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(i.uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w("TvControls", "cannot open ${i.uri}", e)
        }
    }

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

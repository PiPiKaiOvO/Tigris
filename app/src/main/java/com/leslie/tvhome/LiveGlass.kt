package com.leslie.tvhome

import android.annotation.TargetApi
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.view.View
import kotlin.math.ceil

/** One shared, low-resolution GPU blur layer sampled by every floating control. */
@TargetApi(31)
class LiveGlass(private val scene: View) {
    private val node = RenderNode("shared live glass")
    private val scale = 0.25f
    private var dirty = true
    private var lastW = 0
    private var lastH = 0

    init {
        // Heavy blur, then the colour lifted and saturated so the glass takes on what is behind it.
        val blur = scene.dp(20) * scale
        val tone = ColorMatrix().apply {
            setSaturation(1.5f)
            postConcat(ColorMatrix(floatArrayOf(
                0.88f, 0f, 0f, 0f, 22f,
                0f, 0.88f, 0f, 0f, 22f,
                0f, 0f, 0.88f, 0f, 22f,
                0f, 0f, 0f, 1f, 0f)))
        }
        node.setRenderEffect(RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(tone),
            RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)))
        node.setUseCompositingLayer(true, null)
    }

    fun invalidate() { dirty = true }

    fun draw(canvas: Canvas) {
        if (!canvas.isHardwareAccelerated) { scene.draw(canvas); return }
        val w = ceil(scene.width * scale).toInt().coerceAtLeast(1)
        val h = ceil(scene.height * scale).toInt().coerceAtLeast(1)
        if (dirty || w != lastW || h != lastH || !node.hasDisplayList()) {
            node.setPosition(0, 0, w, h)
            val recording = node.beginRecording(w, h)
            recording.scale(scale, scale)
            scene.draw(recording)
            node.endRecording()
            dirty = false
            lastW = w; lastH = h
        }
        val save = canvas.save()
        canvas.scale(1f / scale, 1f / scale)
        canvas.drawRenderNode(node)
        canvas.restoreToCount(save)
    }

    fun dispose() = node.discardDisplayList()
}

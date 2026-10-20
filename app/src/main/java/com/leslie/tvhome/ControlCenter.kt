package com.leslie.tvhome

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * The tvOS Control Center: a frosted-glass panel in the top-right corner with
 * the clock, a row of round icon buttons, brightness and volume sliders and
 * the input source. Opened with Back (or Menu) on the home screen, or the
 * toggles icon in the top-right.
 */
class ControlCenter(private val home: HomeActivity, private val host: FrameLayout) {
    private val ctx = home
    private val prefs = Prefs(ctx)
    private val radius = ctx.dp(34).toFloat()
    private val width = ctx.dp(440)

    private val scrim = View(ctx).apply {
        setBackgroundColor(Color.argb(70, 0, 0, 0))
        alpha = 0f
        translationZ = ctx.dp(100).toFloat()
    }
    private val glass = GlassDrawable()
    private val panel = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = glass
        clipChildren = false
        clipToPadding = false
        setPadding(ctx.dp(26), ctx.dp(22), ctx.dp(26), ctx.dp(24))
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        elevation = ctx.dp(30).toFloat()
        translationZ = ctx.dp(100).toFloat()
        alpha = 0f
    }
    private val clock = ctx.text(30f, weight = 600).apply { letterSpacing = -0.01f }
    private val date = ctx.text(15f, Color.argb(160, 255, 255, 255), weight = 500)
    private val memButton: RoundButton
    private val brightness: Slider
    private val volume: Slider
    private val inputModule: Module
    private var inputs: List<Bravia.Input> = emptyList()
    private var volumeTarget = "speaker"
    private val status = ctx.text(12f, Color.argb(120, 255, 255, 255))
    private val focusables = ArrayList<View>()
    private val io = Executors.newSingleThreadExecutor()
    private val tv = TvControls(ctx)
    private val ticker = object : Runnable {
        override fun run() {
            updateClock()
            followVolume()
            host.postDelayed(this, if (isOpen) 2_000 else 15_000)
        }
    }

    /** Keeps the volume slider in step with the remote's own volume keys while the panel is open. */
    private fun followVolume() {
        if (!isOpen) return
        io.execute {
            val v = tv.volume() ?: return@execute
            host.post {
                // Do not fight a slider the user is holding; only follow outside the panel's own input.
                if (isOpen && !volume.hasFocus()) volume.set(v)
            }
        }
    }

    var isOpen = false
        private set

    init {
        val header = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        header.addView(clock)
        header.addView(date, LinearLayout.LayoutParams(-2, -2).apply { marginStart = ctx.dp(12); bottomMargin = ctx.dp(3) })
        panel.addView(header, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = ctx.dp(18) })

        val circles = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        fun circle(label: String, icon: Int, action: () -> Unit) =
            RoundButton(label, icon, action).also {
                focusables += it
                circles.addView(it, LinearLayout.LayoutParams(0, -2, 1f))
            }
        circle("待机", R.drawable.ic_moon) { needAdb { close(); Cleaner.sleep(ctx) } }
        circle("关机", R.drawable.ic_power) { confirm("关机", "电视会彻底断电关闭，用遥控器电源键开机。") { Cleaner.shutdown(ctx) } }
        circle("重启", R.drawable.ic_restart) { confirm("重启", "电视会重新启动，大约需要一分钟。") { Cleaner.reboot(ctx) } }
        memButton = circle("释放内存", R.drawable.ic_memory) { cleanNow() }
        circle("相框", R.drawable.ic_photo) {
            close()
            ctx.startActivity(Intent(ctx, FrameActivity::class.java))
            ctx.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        circle("设置", R.drawable.ic_gear) {
            close()
            ctx.startActivity(Intent(ctx, SettingsActivity::class.java))
        }
        panel.addView(circles, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = -ctx.dp(6); rightMargin = -ctx.dp(6)
        })

        // Shown only where a real backlight level can be read and set (Sony); on TCL the slider
        // would only open the maker's settings panel and show a made-up level, so it stays hidden.
        brightness = Slider(R.drawable.ic_sun, "亮度") { v -> tv.setBrightness(v) }
        brightness.visibility = View.GONE
        volume = Slider(R.drawable.ic_volume, "音量", perPress = 2) { v -> tv.setVolume(volumeTarget, v) }.also { focusables += it }
        panel.addView(volume, LinearLayout.LayoutParams(-1, ctx.dp(54)).apply { topMargin = ctx.dp(12) })

        inputModule = Module(R.drawable.ic_input, Color.rgb(10, 132, 255), "输入源") { pickInput() }.also { focusables += it }
        inputModule.sub.visibility = View.VISIBLE
        panel.addView(inputModule, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.dp(14) })

        panel.addView(status, LinearLayout.LayoutParams(-2, -2).apply { topMargin = ctx.dp(14); leftMargin = ctx.dp(4) })

        // The focused control lights up the glass around it; the light glides to the next one.
        host.viewTreeObserver.addOnGlobalFocusChangeListener { _, now -> if (isOpen && now != null) glowAt(now) }
    }

    private var glowAnim: android.animation.ValueAnimator? = null

    private fun glowAt(v: View) {
        var p: View? = v
        while (p != null && p !== panel) p = p.parent as? View
        if (p == null) return
        // Focus is given on open, before the panel has been laid out.
        if (v.width == 0) { v.post { if (v.isFocused) glowAt(v) }; return }
        val r = android.graphics.Rect(0, 0, v.width, v.height)
        panel.offsetDescendantRectToMyCoords(v, r)
        val fromX = glass.glowX
        val fromY = glass.glowY
        val fromA = glass.glow
        glowAnim?.cancel()
        glowAnim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260
            interpolator = Motion.ease
            addUpdateListener {
                val f = it.animatedFraction
                glass.glowX = if (fromA == 0f) r.exactCenterX() else fromX + (r.exactCenterX() - fromX) * f
                glass.glowY = if (fromA == 0f) r.exactCenterY() else fromY + (r.exactCenterY() - fromY) * f
                glass.glow = fromA + (1f - fromA) * f
                glass.glowR = max(r.width(), r.height()) * 0.5f + ctx.dp(110)
                panel.invalidate()
            }
            start()
        }
    }

    // ---- glass ----------------------------------------------------------------

    /**
     * Frosted glass: a heavily blurred, saturated copy of whatever is behind the
     * panel, a dark tint, and a hairline light edge. The blurred copy is taken
     * once when the panel opens and drawn in screen space so it stays put while
     * the panel slides.
     */
    private inner class GlassDrawable : Drawable() {
        var blur: Bitmap? = null
        /** Light from the focused control, in panel coordinates; [glow] is its strength 0..1. */
        var glowX = 0f
        var glowY = 0f
        var glowR = 1f
        var glow = 0f
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glowEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ctx.dp(2).toFloat()
        }
        private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.7f) })
        }
        private val tint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(118, 22, 22, 24) }
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ctx.dp(1.2f).toFloat()
        }
        private val path = Path()
        private val r = RectF()

        override fun draw(c: Canvas) {
            r.set(bounds)
            path.reset()
            path.addRoundRect(r, radius, radius, Path.Direction.CW)
            c.save()
            c.clipPath(path)
            val b = blur
            if (b != null) {
                c.drawBitmap(b, null, RectF(-panel.x, -panel.y, host.width - panel.x, host.height - panel.y), bmpPaint)
            }
            c.drawRect(r, tint)
            if (glow > 0f) {
                glowPaint.shader = android.graphics.RadialGradient(glowX, glowY, glowR,
                    intArrayOf(Color.argb((125 * glow).toInt(), 255, 255, 255), Color.argb((48 * glow).toInt(), 255, 255, 255), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.45f, 1f), android.graphics.Shader.TileMode.CLAMP)
                c.drawRect(r, glowPaint)
            }
            c.restore()
            // Specular rim: brighter along the top edge, like Liquid Glass.
            edge.shader = android.graphics.LinearGradient(0f, r.top, 0f, r.bottom,
                intArrayOf(Color.argb(110, 255, 255, 255), Color.argb(26, 255, 255, 255), Color.argb(50, 255, 255, 255)),
                floatArrayOf(0f, 0.5f, 1f), android.graphics.Shader.TileMode.CLAMP)
            val inset = edge.strokeWidth / 2
            r.inset(inset, inset)
            c.drawRoundRect(r, radius - inset, radius - inset, edge)
            if (glow > 0f) {
                // The rim catches the light where it passes close to the focused control.
                glowEdge.shader = android.graphics.RadialGradient(glowX, glowY, glowR * 2.2f,
                    intArrayOf(Color.argb((230 * glow).toInt(), 255, 255, 255), Color.TRANSPARENT),
                    null, android.graphics.Shader.TileMode.CLAMP)
                c.drawRoundRect(r, radius - inset, radius - inset, glowEdge)
            }
        }

        override fun getOutline(outline: android.graphics.Outline) =
            outline.setRoundRect(bounds, radius)

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private fun snapshotBehind(): Bitmap? = try {
        val scale = 0.25f
        val b = Bitmap.createBitmap((host.width * scale).toInt(), (host.height * scale).toInt(), Bitmap.Config.ARGB_8888)
        Canvas(b).apply { scale(scale, scale); host.draw(this) }
        Wallpapers.blurred(b, darken = 0f).also { b.recycle() }
    } catch (e: Throwable) {
        null
    }

    // ---- controls ---------------------------------------------------------------

    /** A round, icon-only button; its name fades in below it when focused. */
    private inner class RoundButton(private val label: String, icon: Int, action: () -> Unit) : LinearLayout(ctx) {
        private val size = ctx.dp(58)
        private val disc = FrameLayout(ctx).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(46, 255, 255, 255)) }
        }
        private val img = ImageView(ctx).apply { setImageResource(icon); setColorFilter(Color.WHITE) }
        private val name = ctx.text(13f, weight = 500).apply { text = label; alpha = 0f; gravity = Gravity.CENTER; maxLines = 1 }
        private val reset = Runnable { name.text = label; if (!isFocused) name.animate().alpha(0f).start() }

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            isFocusable = true
            isClickable = true
            disc.addView(img, FrameLayout.LayoutParams(ctx.dp(26), ctx.dp(26), Gravity.CENTER))
            addView(disc, LayoutParams(size, size))
            addView(name, LayoutParams(-2, -2).apply { topMargin = ctx.dp(10) })
            setOnClickListener { action() }
            setOnFocusChangeListener { _, f ->
                (disc.background as GradientDrawable).setColor(if (f) Color.WHITE else Color.argb(46, 255, 255, 255))
                img.setColorFilter(if (f) Color.BLACK else Color.WHITE)
                val s = if (f) 1.14f else 1f
                disc.animate().scaleX(s).scaleY(s).translationZ(if (f) ctx.dp(10).toFloat() else 0f)
                    .setDuration(220).setInterpolator(if (f) Motion.spring else Motion.ease).start()
                name.animate().alpha(if (f) 1f else 0f).translationY(if (f) ctx.dp(4).toFloat() else 0f).setDuration(160).start()
            }
        }

        /** Shows [msg] under the button for a while (in place of an Android toast); 0 = until replaced. */
        fun flash(msg: String, ms: Long = 4000) {
            name.text = msg
            name.animate().alpha(1f).setDuration(120).start()
            removeCallbacks(reset)
            if (ms > 0) postDelayed(reset, ms)
        }
    }

    /** A wide rounded module: coloured icon badge, title, optional subtitle. */
    private open inner class Module(icon: Int, badge: Int, label: String, action: () -> Unit) : LinearLayout(ctx) {
        private val idle = rounded(Color.argb(40, 255, 255, 255), ctx.dp(24).toFloat())
        private val focus = rounded(Color.WHITE, ctx.dp(24).toFloat())
        protected val title = ctx.text(17f, weight = 600).apply { text = label }
        val sub: TextView = ctx.text(13f, Color.argb(150, 255, 255, 255), weight = 500).apply { visibility = GONE }
        protected val body = LinearLayout(ctx).apply { orientation = VERTICAL; gravity = Gravity.CENTER_VERTICAL }

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            background = idle
            minimumHeight = ctx.dp(70)
            setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(20), ctx.dp(12))
            val badgeView = FrameLayout(ctx).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(badge) }
                addView(ImageView(ctx).apply { setImageResource(icon); setColorFilter(Color.WHITE) },
                    FrameLayout.LayoutParams(ctx.dp(22), ctx.dp(22), Gravity.CENTER))
            }
            addView(badgeView, LayoutParams(ctx.dp(42), ctx.dp(42)).apply { marginEnd = ctx.dp(14) })
            body.addView(title)
            body.addView(sub, LayoutParams(-2, -2).apply { topMargin = ctx.dp(4) })
            addView(body, LayoutParams(0, -2, 1f))
            setOnClickListener { action() }
            setOnFocusChangeListener { _, f ->
                background = if (f) focus else idle
                tint(f)
                animate().scaleX(if (f) 1.05f else 1f).scaleY(if (f) 1.05f else 1f)
                    .translationZ(if (f) ctx.dp(12).toFloat() else 0f)
                    .setDuration(220).setInterpolator(if (f) Motion.spring else Motion.ease).start()
            }
        }

        protected open fun tint(f: Boolean) {
            title.setTextColor(if (f) Color.BLACK else Color.WHITE)
            sub.setTextColor(if (f) Color.argb(150, 0, 0, 0) else Color.argb(150, 255, 255, 255))
        }
    }

    /**
     * iOS-style capsule slider: the white fill is the value. Left/right change it,
     * and the TV is updated a moment after the last key press.
     */
    private inner class Slider(
        icon: Int,
        label: String,
        private val perPress: Int = 0,
        private val send: (Int) -> Unit,
    ) : FrameLayout(ctx) {
        private val corner = ctx.dp(27).toFloat()
        private val fill = View(ctx)
        private val img = ImageView(ctx).apply { setImageResource(icon) }
        private val nameText = ctx.text(15f, weight = 600).apply { text = label }
        private val valueText = ctx.text(15f, weight = 600)
        private var value = 0
        private var min = 0
        private var max = 100
        private var ready = false
        private val push = Runnable { val v = value; io.execute { send(v) } }

        init {
            isFocusable = true
            clipRounded(corner)
            background = rounded(Color.argb(40, 255, 255, 255), corner)
            addView(fill, LayoutParams(0, -1))
            addView(img, LayoutParams(ctx.dp(24), ctx.dp(24), Gravity.CENTER_VERTICAL or Gravity.START).apply { marginStart = ctx.dp(18) })
            addView(nameText, LayoutParams(-2, -2, Gravity.CENTER_VERTICAL or Gravity.START).apply { marginStart = ctx.dp(54) })
            addView(valueText, LayoutParams(-2, -2, Gravity.CENTER_VERTICAL or Gravity.END).apply { marginEnd = ctx.dp(20) })
            setOnFocusChangeListener { _, f ->
                background = rounded(Color.argb(if (f) 70 else 40, 255, 255, 255), corner)
                animate().scaleX(if (f) 1.05f else 1f).scaleY(if (f) 1.05f else 1f)
                    .translationZ(if (f) ctx.dp(12).toFloat() else 0f)
                    .setDuration(220).setInterpolator(if (f) Motion.spring else Motion.ease).start()
                draw()
            }
            setOnKeyListener { _, code, e ->
                if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return@setOnKeyListener false
                if (e.action == KeyEvent.ACTION_DOWN && ready) {
                    val dir = if (code == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                    val step = if (perPress > 0) perPress else ((max - min) / 25).coerceAtLeast(1)
                    val nv = (value + dir * step).coerceIn(min, max)
                    if (nv == value) {
                        if (e.repeatCount == 0) Motion.bump(this, dir, 0)
                    } else {
                        value = nv
                        draw()
                        removeCallbacks(push)
                        postDelayed(push, 150)
                    }
                }
                true
            }
            set(null)
        }

        fun set(level: Bravia.Level?) {
            ready = level != null
            if (level != null) { value = level.value; min = level.min; max = level.max }
            alpha = if (ready) 1f else 0.45f
            draw()
        }

        private fun draw() {
            valueText.text = if (ready) value.toString() else "—"
            val frac = if (ready && max > min) (value - min).toFloat() / (max - min) else 0f
            post {
                val w = (width * frac).toInt()
                fill.layoutParams = fill.layoutParams.apply { width = w }
                fill.setBackgroundColor(if (isFocused) Color.WHITE else Color.argb(215, 255, 255, 255))
                // Glyphs the fill has reached turn dark, as on iOS.
                val dark = Color.argb(220, 0, 0, 0)
                img.setColorFilter(if (w > ctx.dp(42)) dark else Color.WHITE)
                nameText.setTextColor(if (w > ctx.dp(54) + nameText.width) dark else Color.WHITE)
                valueText.setTextColor(if (w > width - ctx.dp(20)) dark else Color.WHITE)
            }
        }
    }

    // ---- open / close -------------------------------------------------------------

    fun open() {
        if (isOpen) return
        isOpen = true
        glass.blur = snapshotBehind()
        glass.glow = 0f
        refresh()
        home.setGridBlocked(true)
        host.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        host.addView(panel, FrameLayout.LayoutParams(width, -2, Gravity.TOP or Gravity.END).apply {
            topMargin = ctx.dp(26); marginEnd = ctx.dp(26)
        })
        panel.translationX = ctx.dp(60).toFloat()
        panel.scaleX = 0.96f
        panel.scaleY = 0.96f
        panel.pivotX = width.toFloat()
        panel.pivotY = 0f
        scrim.animate().alpha(1f).setDuration(220).start()
        panel.animate().alpha(1f).translationX(0f).scaleX(1f).scaleY(1f).setDuration(320)
            .setInterpolator(Motion.ease).setUpdateListener { panel.invalidate() }.start()
        focusables.first().requestFocus()
        host.post(ticker)
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        host.removeCallbacks(ticker)
        scrim.animate().alpha(0f).setDuration(180).start()
        panel.animate().alpha(0f).translationX(ctx.dp(60).toFloat()).scaleX(0.96f).scaleY(0.96f).setDuration(200)
            .setUpdateListener { panel.invalidate() }
            .withEndAction {
                host.removeView(scrim)
                host.removeView(panel)
                glass.blur?.recycle()
                glass.blur = null
            }.start()
        home.restoreGridFocus()
    }

    fun refocus() {
        (focusables.firstOrNull { it.isFocused } ?: memButton).requestFocus()
    }

    fun refresh() {
        updateClock()
        status.text = if (prefs.adbAuthorized) "" else "本机调试未授权：关机、待机、彻底清理不可用"
        status.visibility = if (status.text.isEmpty()) View.GONE else View.VISIBLE
        if (!isOpen) return
        io.execute {
            val b = tv.brightness()
            val v = tv.volume()
            val list = tv.inputs()
            val cur = tv.currentInput()
            host.post {
                brightness.set(b)
                brightness.visibility = if (tv.canSetBacklight) View.VISIBLE else View.GONE
                v?.target?.takeIf { it.isNotEmpty() }?.let { volumeTarget = it }
                volume.set(v)
                inputs = list
                inputModule.sub.text = list.firstOrNull { it.uri == cur }?.name
                    ?: when {
                        list.isEmpty() -> "无法读取电视的输入源"
                        list.first().uri.startsWith("extInput:") -> "当前：主屏幕"
                        else -> "${list.size} 个 HDMI 输入"  // Android can't tell which one is showing
                    }
            }
        }
    }

    private fun updateClock() {
        val now = Date()
        clock.text = SimpleDateFormat("HH:mm", Locale.CHINA).format(now)
        date.text = SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(now)
    }

    private fun pickInput() {
        if (inputs.isEmpty()) return
        // The list replaces the panel instead of stacking on top of it.
        close()
        val sheet = Sheet(host).title("输入源").message(null)
        for (i in inputs) sheet.button(i.name, R.drawable.ic_input) {
            io.execute { tv.setInput(i) }
        }
        sheet.button("取消") {}
        home.showSheet(sheet)
    }

    private fun cleanNow() {
        memButton.flash("正在释放…", 0)
        Thread {
            val r = Cleaner.clean(ctx.applicationContext)
            host.post {
                // Reported under the button rather than with an Android toast.
                memButton.flash("已释放 ${r.freedMb} MB" + if (r.viaAdb) "" else "（仅缓存）")
            }
        }.start()
    }

    private fun needAdb(action: () -> Unit) {
        if (prefs.adbAuthorized) action() else home.showSheet(Sheet(host).title("需要先授权本机调试")
            .message("点“开始”后，电视会弹出“允许调试吗？”，请勾选“始终允许”，再点“允许”。")
            .button("开始") { home.authorizeAdb() }
            .button("取消") {})
    }

    private fun confirm(title: String, msg: String, action: () -> Unit) = needAdb {
        home.showSheet(Sheet(host).title("$title？").message(msg)
            .button(title) { action() }
            .button("取消") {})
    }
}

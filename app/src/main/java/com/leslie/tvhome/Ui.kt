package com.leslie.tvhome

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

fun Context.dp(v: Number): Int = (v.toFloat() * resources.displayMetrics.density + 0.5f).toInt()
fun View.dp(v: Number): Int = context.dp(v)

fun rounded(color: Int, radius: Float): GradientDrawable =
    GradientDrawable().apply { setColor(color); cornerRadius = radius }

fun View.clipRounded(radiusPx: Float) {
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) =
            outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
    }
    clipToOutline = true
}

object Motion {
    val ease = DecelerateInterpolator(2f)
    val spring = OvershootInterpolator(1.6f)

    /** tvOS "bump" when focus cannot move any further in that direction. */
    fun bump(v: View, dx: Int, dy: Int) {
        v.cameraDistance = v.width * 12f
        v.animate().cancel()
        ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = 260
            addUpdateListener {
                val f = it.animatedValue as Float
                v.rotationY = dx * 7f * f
                v.rotationX = -dy * 7f * f
                v.translationX = dx * v.dp(5) * f
                v.translationY = dy * v.dp(5) * f
            }
            start()
        }
    }
}

const val FONT_BOLD = "sans-serif-medium"

/**
 * Inter (variable, OFL) stands in for Apple's SF: Roboto's digits are what
 * make a screen read as "Android" at a glance. Chinese falls back to the system font.
 */
object Fonts {
    private val cache = HashMap<Int, Typeface>()

    fun inter(ctx: Context, weight: Int): Typeface = cache.getOrPut(weight) {
        try {
            Typeface.Builder(ctx.assets, "inter.ttf")
                .setFontVariationSettings("'wght' $weight")
                .setWeight(weight)
                .setFallback("sans-serif")
                .build() ?: Typeface.DEFAULT
        } catch (e: Exception) {
            Typeface.create(if (weight >= 500) FONT_BOLD else "sans-serif", Typeface.NORMAL)
        }
    }
}

fun Context.text(sizeSp: Float, color: Int = Color.WHITE, bold: Boolean = false, weight: Int = if (bold) 600 else 400): TextView =
    TextView(this).apply {
        textSize = sizeSp
        setTextColor(color)
        typeface = Fonts.inter(this@text, weight)
        includeFontPadding = false
    }

/**
 * A pill button in tvOS style: translucent when idle, solid white with
 * dark text and a slight lift when focused.
 */
class PillButton(ctx: Context, label: String, iconRes: Int? = null, val onClick: () -> Unit) : LinearLayout(ctx) {
    private val tv = ctx.text(17f, bold = true).apply { text = label }
    private val icon: ImageView? = iconRes?.let { ImageView(ctx).apply { setImageResource(it) } }
    private val idle = rounded(Color.argb(46, 255, 255, 255), dp(14).toFloat())
    private val focus = rounded(Color.WHITE, dp(14).toFloat())

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true
        background = idle
        setPadding(dp(24), 0, dp(24), 0)
        icon?.let { addView(it, LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(10) }) }
        addView(tv)
        setOnClickListener { onClick() }
        setOnFocusChangeListener { _, f ->
            background = if (f) focus else idle
            tv.setTextColor(if (f) Color.BLACK else Color.WHITE)
            icon?.setColorFilter(if (f) Color.BLACK else Color.WHITE)
            animate().scaleX(if (f) 1.06f else 1f).scaleY(if (f) 1.06f else 1f)
                .translationZ(if (f) dp(10).toFloat() else 0f).setDuration(160).setInterpolator(Motion.ease).start()
        }
    }
}

/**
 * Full-screen alert in the style of tvOS: dimmed backdrop, centred title,
 * message and a vertical stack of buttons. Back dismisses it.
 */
class Sheet(private val host: FrameLayout) {
    private val ctx = host.context
    val root = FrameLayout(ctx).apply {
        setBackgroundColor(Color.argb(200, 0, 0, 0))
        isClickable = true
        alpha = 0f
        // Above focused tiles, which lift themselves with translationZ.
        translationZ = ctx.dp(200).toFloat()
    }
    private val col = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }
    private val title = ctx.text(28f, bold = true).apply { gravity = Gravity.CENTER }
    private val message = ctx.text(16f, Color.argb(180, 255, 255, 255)).apply {
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.25f)
    }
    private var onDismiss: (() -> Unit)? = null

    init {
        col.addView(title)
        col.addView(message, LinearLayout.LayoutParams(ctx.dp(520), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ctx.dp(12); bottomMargin = ctx.dp(28)
        })
        root.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.setOnKeyListener { _, code, e ->
            if (code == KeyEvent.KEYCODE_BACK && e.action == KeyEvent.ACTION_UP) { dismiss(); true } else false
        }
    }

    fun title(t: String) = apply { title.text = t }
    fun message(m: String?) = apply {
        message.text = m ?: ""
        message.visibility = if (m.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    fun button(label: String, icon: Int? = null, keepOpen: Boolean = false, action: () -> Unit) = apply {
        val b = PillButton(ctx, label, icon) {
            if (!keepOpen) dismiss()
            action()
        }
        col.addView(b, LinearLayout.LayoutParams(ctx.dp(420), ctx.dp(52)).apply { topMargin = ctx.dp(12) })
    }

    fun onDismiss(f: () -> Unit) = apply { onDismiss = f }

    fun show(): Sheet {
        host.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.animate().alpha(1f).setDuration(180).start()
        (0 until col.childCount).map { col.getChildAt(it) }.firstOrNull { it is PillButton }?.requestFocus()
        return this
    }

    val isShowing get() = root.parent != null

    fun dismiss() {
        if (!isShowing) return
        root.animate().alpha(0f).setDuration(140).withEndAction {
            host.removeView(root)
            onDismiss?.invoke()
        }.start()
    }

    /** Back is delivered to the focused child, so buttons forward it here. */
    fun handlesBack(): Boolean { dismiss(); return true }
}

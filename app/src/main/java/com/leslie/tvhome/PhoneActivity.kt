package com.leslie.tvhome

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import java.util.concurrent.Executors

/**
 * A QR code for the phone page ([PhoneServer]). The server runs while this is open and for five
 * minutes after, so photos still on their way arrive.
 */
class PhoneActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var qr: ImageView
    private lateinit var address: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(text(30f, bold = true).apply { text = "用手机设置壁纸" })
            addView(text(18f, Color.argb(200, 255, 255, 255)).apply {
                text = "手机和电视连同一个 Wi-Fi，用相机或微信扫右边的二维码。\n\n" +
                    "• 上传手机里的照片做背景和屏保\n" +
                    "• 填写图片链接，电视会自动去取：可以是一张图片、每次返回不同图片的随机图接口，或者列出图片地址的网页 / JSON\n" +
                    "• 选择背景和屏保（照片或 Apple TV 航拍）"
                setLineSpacing(0f, 1.3f)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
            address = text(16f, Color.argb(150, 255, 255, 255))
            addView(address, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })
        }
        qr = ImageView(this).apply {
            background = rounded(Color.WHITE, dp(18).toFloat())
            setPadding(dp(22), dp(22), dp(22), dp(22))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(16, 16, 18))
            setPadding(dp(96), dp(48), dp(96), dp(48))
            addView(left, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(64) })
            addView(qr, LinearLayout.LayoutParams(dp(360), dp(360)))
        })
    }

    override fun onStart() {
        super.onStart()
        stopLater.removeCallbacks(stop)
        address.text = "正在启动…"
        io.execute {
            val url = PhoneServer.start(this)
            val bmp = url?.let { PhoneServer.qr(it) }
            runOnUiThread {
                if (url == null) {
                    address.text = "电视没有连上网络，连上后再打开这里。"
                    qr.setImageDrawable(null)
                } else {
                    address.text = url
                    qr.setImageBitmap(bmp)
                }
            }
        }
    }

    override fun onStop() {
        stopLater.postDelayed(stop, 5 * 60_000L)
        super.onStop()
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private val stopLater = Handler(Looper.getMainLooper())
        private val stop = Runnable { Thread { PhoneServer.stop() }.start() }
    }
}

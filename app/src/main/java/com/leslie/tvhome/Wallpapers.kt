package com.leslie.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Bing's daily photos (cn.bing.com works without a VPN), cached on disk.
 * The last ~15 days are fetched once a day and older ones are kept up to
 * [MAX_FILES], so the home background and the photo frame can rotate through them.
 */
class Wallpapers(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "wall").apply { mkdirs() }
    private val metaFile = File(dir, "meta.json")

    /** Cached files, newest first. */
    fun files(): List<File> =
        dir.listFiles { f -> f.name.endsWith(".jpg") }?.sortedByDescending { it.name } ?: emptyList()

    /** The photo for the current time slot when rotating every [minutes]. */
    fun current(minutes: Int): File? {
        val all = files()
        if (all.isEmpty()) return null
        val slot = System.currentTimeMillis() / (minutes.coerceAtLeast(1) * 60_000L)
        return all[(slot % all.size).toInt()]
    }

    /** "Title — place (© photographer)" for the photo frame caption. */
    fun caption(f: File): String = try {
        JSONObject(metaFile.readText()).optString(f.name)
    } catch (e: Exception) { "" }

    /** Blocking; fetches new photos if the last refresh is older than ~a day. */
    fun refresh(force: Boolean = false) {
        val stamp = File(dir, "stamp")
        if (!force && stamp.exists() && metaFile.exists() && System.currentTimeMillis() - stamp.lastModified() < 20 * 3_600_000L && files().size >= 4) return
        val meta = try { JSONObject(metaFile.readText()) } catch (e: Exception) { JSONObject() }
        try {
            for (idx in listOf(0, 7)) {
                val json = get("https://cn.bing.com/HPImageArchive.aspx?format=js&idx=$idx&n=8&mkt=zh-CN").decodeToString()
                val imgs = JSONObject(json).getJSONArray("images")
                for (i in 0 until imgs.length()) {
                    val o = imgs.getJSONObject(i)
                    val name = o.getString("startdate") + ".jpg"
                    val title = o.optString("title")
                    val copy = o.optString("copyright")
                    meta.put(name, if (title.isNotEmpty() && !copy.startsWith(title)) "$title — $copy" else copy)
                    val f = File(dir, name)
                    if (f.exists() && f.length() > 0) continue
                    val tmp = File(dir, "$name.part")
                    tmp.writeBytes(get("https://cn.bing.com" + o.getString("urlbase") + "_1920x1080.jpg"))
                    tmp.renameTo(f)
                }
            }
            files().drop(MAX_FILES).forEach { meta.remove(it.name); it.delete() }
            metaFile.writeText(meta.toString())
            stamp.writeText(System.currentTimeMillis().toString())
        } catch (e: Exception) {
            Log.w("Wallpapers", "refresh failed", e)
            if (meta.length() > 0) metaFile.writeText(meta.toString())
        }
    }

    private fun get(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 15000
        return c.inputStream.use { it.readBytes() }
    }

    fun decode(f: File, full: Boolean = false): Bitmap? = try {
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply {
            inPreferredConfig = if (full) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
            inMutable = true
        })
    } catch (e: OutOfMemoryError) { null }

    /** A photo dressed for the home screen: darker at the bottom and top-left so tiles and clock stay legible. */
    fun forHome(f: File): Bitmap? = decode(f)?.also { b ->
        val c = Canvas(b)
        val w = b.width.toFloat()
        val h = b.height.toFloat()
        val p = Paint()
        p.shader = LinearGradient(0f, h * 0.35f, 0f, h, Color.TRANSPARENT, Color.argb(215, 0, 0, 0), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = android.graphics.RadialGradient(w * 0.12f, h * 0.38f, w * 0.45f,
            Color.argb(90, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
    }

    companion object {
        const val MAX_FILES = 40

        /** Cheap, smooth blur: shrink, box-blur three times, let the view scale it back up. */
        fun blurred(src: Bitmap, darken: Float = 0.45f): Bitmap {
            val w = 96
            val h = (w * src.height / src.width.toFloat()).toInt().coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
            val px = IntArray(w * h)
            small.getPixels(px, 0, w, 0, 0, w, h)
            repeat(3) { boxBlur(px, w, h, 4) }
            for (i in px.indices) {
                val c = px[i]
                px[i] = Color.rgb(
                    (Color.red(c) * (1 - darken)).toInt(),
                    (Color.green(c) * (1 - darken)).toInt(),
                    (Color.blue(c) * (1 - darken)).toInt(),
                )
            }
            small.setPixels(px, 0, w, 0, 0, w, h)
            return small
        }

        /** Dark vertical gradient used before any wallpaper is cached. */
        fun fallback(): Bitmap {
            val b = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888)
            Paint().apply {
                shader = LinearGradient(0f, 0f, 32f, 18f, Color.rgb(38, 44, 66), Color.rgb(10, 10, 14), Shader.TileMode.CLAMP)
                Canvas(b).drawRect(0f, 0f, 32f, 18f, this)
            }
            return b
        }

        private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
            val tmp = IntArray(px.size)
            pass(px, tmp, w, h, r, true)
            pass(tmp, px, w, h, r, false)
        }

        private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
            val len = if (horizontal) w else h
            val lines = if (horizontal) h else w
            for (line in 0 until lines) {
                for (i in 0 until len) {
                    var rs = 0; var gs = 0; var bs = 0; var n = 0
                    for (k in -r..r) {
                        val j = (i + k).coerceIn(0, len - 1)
                        val c = if (horizontal) src[line * w + j] else src[j * w + line]
                        rs += (c shr 16) and 0xff; gs += (c shr 8) and 0xff; bs += c and 0xff; n++
                    }
                    val out = (0xff shl 24) or ((rs / n) shl 16) or ((gs / n) shl 8) or (bs / n)
                    if (horizontal) dst[line * w + i] = out else dst[i * w + line] = out
                }
            }
        }
    }
}

/** The soft multi-colour gradients tvOS 26 uses behind the home grid. */
object Backgrounds {
    /** key -> (label, colour blobs as x, y, radius (fractions of width), colour). */
    val THEMES: LinkedHashMap<String, Pair<String, List<Blob>>> = linkedMapOf(
        "green" to ("绿色" to listOf(
            Blob(0.10f, 0.35f, 0.75f, 0xFF0B7A2E.toInt()),
            Blob(0.48f, -0.05f, 0.70f, 0xFF02B43C.toInt()),
            Blob(1.00f, 0.20f, 0.55f, 0xFF0F7F8A.toInt()),
            Blob(1.05f, 0.80f, 0.45f, 0xFF3C2B8F.toInt()),
        )),
        "blue" to ("蓝色" to listOf(
            Blob(0.10f, 0.30f, 0.75f, 0xFF0B3C8C.toInt()),
            Blob(0.50f, -0.05f, 0.70f, 0xFF1E6FE0.toInt()),
            Blob(1.00f, 0.20f, 0.55f, 0xFF16A3C4.toInt()),
            Blob(1.05f, 0.85f, 0.45f, 0xFF5B2EA6.toInt()),
        )),
        "purple" to ("紫色" to listOf(
            Blob(0.10f, 0.30f, 0.75f, 0xFF4A1C8C.toInt()),
            Blob(0.50f, -0.05f, 0.70f, 0xFF8E3BE0.toInt()),
            Blob(1.00f, 0.20f, 0.55f, 0xFFD24C9A.toInt()),
            Blob(1.05f, 0.85f, 0.45f, 0xFF2D3FA8.toInt()),
        )),
        "orange" to ("橙色" to listOf(
            Blob(0.10f, 0.30f, 0.75f, 0xFF9A3412.toInt()),
            Blob(0.50f, -0.05f, 0.70f, 0xFFF0802A.toInt()),
            Blob(1.00f, 0.20f, 0.55f, 0xFFE0466A.toInt()),
            Blob(1.05f, 0.85f, 0.45f, 0xFF6B2FA0.toInt()),
        )),
        "graphite" to ("深灰" to listOf(
            Blob(0.10f, 0.30f, 0.75f, 0xFF2A2D34.toInt()),
            Blob(0.50f, -0.05f, 0.70f, 0xFF4A505C.toInt()),
            Blob(1.00f, 0.20f, 0.55f, 0xFF36404F.toInt()),
            Blob(1.05f, 0.85f, 0.45f, 0xFF2B2A40.toInt()),
        )),
    )

    data class Blob(val x: Float, val y: Float, val r: Float, val color: Int)

    /** Rendered small; the ImageView's bilinear upscale keeps it perfectly smooth. */
    fun render(key: String): Bitmap {
        val blobs = (THEMES[key] ?: THEMES.getValue("green")).second
        val w = 480
        val h = 270
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.rgb(6, 10, 10))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (bl in blobs) {
            p.shader = android.graphics.RadialGradient(bl.x * w, bl.y * w, bl.r * w,
                intArrayOf(bl.color, Color.argb(140, Color.red(bl.color), Color.green(bl.color), Color.blue(bl.color)), Color.TRANSPARENT),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        // Darken the bottom so the lower rows sit on near-black, as in tvOS.
        p.shader = LinearGradient(0f, h * 0.45f, 0f, h.toFloat(),
            Color.TRANSPARENT, Color.argb(235, 0, 0, 0), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        return b
    }
}

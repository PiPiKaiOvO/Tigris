package com.leslie.tvhome

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.palette.graphics.Palette
import java.text.Collator
import java.util.Locale

data class AppEntry(
    val pkg: String,
    val label: String,
    val component: ComponentName,
    val leanback: Boolean,
)

/** Enumerates launchable apps and renders their 5:3 tvOS-style tiles. */
class AppRepo(private val ctx: Context) {
    private val pm: PackageManager = ctx.packageManager
    private val prefs = Prefs(ctx)
    private val tiles = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun allApps(): List<AppEntry> {
        val byPkg = LinkedHashMap<String, AppEntry>()
        fun add(list: List<ResolveInfo>, leanback: Boolean) {
            for (ri in list) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == ctx.packageName && !leanback) continue
                if (byPkg[ai.packageName]?.leanback == true) continue
                byPkg[ai.packageName] = AppEntry(
                    ai.packageName,
                    ri.loadLabel(pm).toString().trim(),
                    ComponentName(ai.packageName, ai.name),
                    leanback,
                )
            }
        }
        add(pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0), true)
        add(pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0), false)
        // Other launchers only confuse things on a launcher grid.
        HIDE_ALWAYS.forEach { byPkg.remove(it) }
        return byPkg.values.toList()
    }

    /** Visible apps in user order; new apps go to the end. */
    fun orderedVisible(): List<AppEntry> {
        val apps = allApps()
        val hidden = prefs.hidden
        var order = prefs.order
        if (order.isEmpty()) order = Prefs.DEFAULT_ORDER
        val rank = order.withIndex().associate { it.value to it.index }
        val collator = Collator.getInstance(Locale.CHINA)
        return apps.filter { it.pkg !in hidden }
            .sortedWith(compareBy<AppEntry> { rank[it.pkg] ?: Int.MAX_VALUE }
                .thenBy { if (isSystem(it.pkg)) 1 else 0 }
                .thenComparator { a, b -> collator.compare(a.label, b.label) })
    }

    fun isSystem(pkg: String): Boolean = try {
        (pm.getApplicationInfo(pkg, 0).flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
    } catch (e: PackageManager.NameNotFoundException) { false }

    /** Apps the TV needs to work (settings, the TV player, inputs, launchers); never offered for deletion. */
    fun isEssential(pkg: String): Boolean {
        if (pkg == ctx.packageName || pkg in ESSENTIAL) return true
        if (listOf(".settings", "tvinput", "launcher", "systemui", "inputmethod", ".ime").any { it in pkg.lowercase() }) return true
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryIntentActivities(home, PackageManager.MATCH_DISABLED_COMPONENTS).any { it.activityInfo.packageName == pkg }
    }

    fun launchIntent(app: AppEntry): Intent? {
        val i = if (app.leanback) pm.getLeanbackLaunchIntentForPackage(app.pkg) else null
        return (i ?: pm.getLaunchIntentForPackage(app.pkg) ?: Intent(Intent.ACTION_MAIN).setComponent(app.component))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    fun cachedTile(pkg: String): Bitmap? = tiles.get(pkg)

    /** Builds the tile bitmap; call off the main thread. */
    fun tile(app: AppEntry): Bitmap {
        tiles.get(app.pkg)?.let { return it }
        val bmp = Bitmap.createBitmap(TILE_W, TILE_H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val banner = try {
            pm.getActivityInfo(app.component, 0).loadBanner(pm)
                ?: pm.getApplicationInfo(app.pkg, 0).loadBanner(pm)
        } catch (e: Exception) { null }
        if (banner != null) {
            drawCenterCrop(c, banner)
        } else {
            val icon = try { pm.getActivityIcon(app.component) } catch (e: Exception) { pm.defaultActivityIcon }
            drawIconTile(c, icon)
        }
        tiles.put(app.pkg, bmp)
        return bmp
    }

    fun invalidate() = tiles.evictAll()

    private fun drawCenterCrop(c: Canvas, d: Drawable) {
        val iw = d.intrinsicWidth.takeIf { it > 0 } ?: TILE_W
        val ih = d.intrinsicHeight.takeIf { it > 0 } ?: TILE_H
        val scale = maxOf(TILE_W.toFloat() / iw, TILE_H.toFloat() / ih)
        val w = (iw * scale).toInt()
        val h = (ih * scale).toInt()
        val l = (TILE_W - w) / 2
        val t = (TILE_H - h) / 2
        c.drawColor(Color.BLACK)
        d.setBounds(l, t, l + w, t + h)
        d.draw(c)
    }

    /**
     * Icon-only apps: the icon blown up and blurred fills the tile, with the
     * crisp icon in the middle — how tvOS 26 frames non-TV apps.
     */
    private fun drawIconTile(c: Canvas, icon: Drawable) {
        val src = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 192, 192)
        icon.draw(Canvas(src))
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val base = Palette.from(src).maximumColorCount(12).generate().let {
            it.lightVibrantSwatch ?: it.vibrantSwatch ?: it.dominantSwatch
        }?.rgb ?: Color.rgb(120, 130, 150)
        c.drawColor(base)
        // Crop the centre of the icon, stretch it over the tile and blur it.
        val crop = Bitmap.createBitmap(src, 24, 40, 144, 112)
        val blurred = Wallpapers.blurred(crop, darken = 0f)
        p.alpha = 235
        c.drawBitmap(blurred, null, RectF(-40f, -40f, TILE_W + 40f, TILE_H + 40f), p)
        p.alpha = 255
        // A light veil like frosted glass.
        c.drawColor(Color.argb(22, 255, 255, 255))
        val s = (TILE_H * 0.58f).toInt()
        val dst = RectF(((TILE_W - s) / 2).toFloat(), ((TILE_H - s) / 2).toFloat(),
            ((TILE_W + s) / 2).toFloat(), ((TILE_H + s) / 2).toFloat())
        c.drawBitmap(src, Rect(0, 0, 192, 192), dst, p)
        crop.recycle(); blurred.recycle(); src.recycle()
    }

    companion object {
        const val TILE_W = 400
        const val TILE_H = 225
        val HIDE_ALWAYS = setOf(
            "com.oversea.aslauncher",
            "com.dangbei.TVHomeLauncher",
            "com.android.tv.settings.system.FallbackHome",
        )
        private val ESSENTIAL = setOf(
            "com.sony.dtv.tvx",                // Sony's TV / HDMI player
            "com.android.tv",                  // Live Channels
            "com.google.android.tv",
            "com.android.vending",
            "com.google.android.gms",
            "com.sony.dtv.systemupdate",
            "com.sony.dtv.update",
        )
    }
}

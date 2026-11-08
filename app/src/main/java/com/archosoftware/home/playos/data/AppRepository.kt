package com.archosoftware.home.playos.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.get
import androidx.core.graphics.scale
import java.util.concurrent.ConcurrentHashMap

data class AppEntry(
    val label: String,
    val packageName: String,
)

data class AppIcon(
    val bitmap: ImageBitmap,
    val fullBleed: Boolean,
)

class AppRepository(private val context: Context) {

    private val pm = context.packageManager
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Cache ~34 icons at 192px (5MB budget)
    private val iconCache = object : LruCache<String, AppIcon>(5 * 1024) {
        override fun sizeOf(key: String, value: AppIcon): Int =
            (value.bitmap.width * value.bitmap.height * 4) / 1024
    }

    private val accentCache = ConcurrentHashMap<String, Color>()

    fun loadApps(): List<AppEntry> {
        val tvIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val phoneIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val selfPackage = context.packageName

        // TV apps first, then sideloaded mobile apps
        return (query(tvIntent) + query(phoneIntent))
            .asSequence()
            .distinctBy { it.activityInfo.packageName }
            .filter { !it.activityInfo.packageName.equals(selfPackage, ignoreCase = true) }
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { (_, packageName) -> !packageName.equals(selfPackage, ignoreCase = true) }
            .sortedBy { (label) -> label.lowercase() }
            .toList()
    }

    fun icon(packageName: String): AppIcon? {
        iconCache[packageName]?.let { return it }
        return runCatching { render(pm.getApplicationIcon(packageName)) }
            .getOrNull()
            ?.also { iconCache.put(packageName, it) }
    }

    // Dominant accent color for background tinting
    fun accent(packageName: String): Color {
        accentCache[packageName]?.let { return it }
        val source = icon(packageName)?.bitmap?.asAndroidBitmap() ?: return DefaultAccent

        val small = source.scale(8, 8, filter = true)
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val p = small[x, y]
                if ((p ushr 24) < 128) continue
                val pr = (p shr 16) and 0xFF
                val pg = (p shr 8) and 0xFF
                val pb = p and 0xFF
                val lum = (pr + pg + pb) / 3
                if (lum !in (24..235)) continue
                r += pr
                g += pg
                b += pb
                n++
            }
        }
        if (small !== source) small.recycle()

        val result = if (n == 0) DefaultAccent
        else Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        accentCache[packageName] = result
        return result
    }

    fun loadFavorites(): Set<String> {
        val selfPackage = context.packageName
        return prefs.getStringSet(KEY_FAVORITES, null)
            ?.asSequence()
            ?.filterNot { it.equals(selfPackage, ignoreCase = true) }
            ?.toSet() ?: emptySet()
    }

    fun setFavorite(packageName: String, favorite: Boolean): Set<String> {
        if (packageName.equals(context.packageName, ignoreCase = true)) return loadFavorites()
        val updated = loadFavorites().toMutableSet()
        if (favorite) updated.add(packageName) else updated.remove(packageName)
        prefs.edit { putStringSet(KEY_FAVORITES, updated) }
        return updated
    }

    fun loadHiddenApps(): Set<String> {
        val selfPackage = context.packageName
        return prefs.getStringSet(KEY_HIDDEN, null)
            ?.asSequence()
            ?.filterNot { it.equals(selfPackage, ignoreCase = true) }
            ?.toSet() ?: emptySet()
    }

    fun setHidden(packageName: String, hidden: Boolean): Set<String> {
        if (packageName.equals(context.packageName, ignoreCase = true)) return loadHiddenApps()
        val updated = loadHiddenApps().toMutableSet()
        if (hidden) updated.add(packageName) else updated.remove(packageName)
        prefs.edit { putStringSet(KEY_HIDDEN, updated) }
        return updated
    }

    fun loadOrder(tab: Int): List<String> {
        val key = if (tab == 0) KEY_ORDER_APPS else KEY_ORDER_MEDIA
        return prefs.getString(key, null)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    }

    fun saveOrder(tab: Int, order: List<String>) {
        val key = if (tab == 0) KEY_ORDER_APPS else KEY_ORDER_MEDIA
        prefs.edit { putString(key, order.joinToString(separator = "\n")) }
    }

    fun applyOrder(apps: List<AppEntry>, savedOrder: List<String>): List<AppEntry> {
        if (savedOrder.isEmpty()) return apps
        val orderMap = HashMap<String, Int>(savedOrder.size)
        for ((index, value) in savedOrder.withIndex()) {
            orderMap[value] = index
        }
        return apps.sortedWith(
            compareBy(
                { (_, packageName) -> orderMap[packageName] ?: Int.MAX_VALUE },
                { (label) -> label.lowercase() },
            ),
        )
    }

    fun killBackgroundProcesses(packageName: String) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses(packageName)
    }

    fun launchIntent(packageName: String): Intent? =
        pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)

    fun clearIconCache() {
        iconCache.evictAll()
        accentCache.clear()
    }

    // Unmask adaptive icons so full tile area is used
    private fun render(drawable: Drawable): AppIcon {
        if (drawable is AdaptiveIconDrawable) {
            val bg: Drawable? = drawable.background
            val fg: Drawable? = drawable.foreground
            if (bg != null) {
                val bitmap = createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val pad = (ICON_PX * 0.25f).toInt()
                bg.setBounds(-pad, -pad, ICON_PX + pad, ICON_PX + pad)
                bg.draw(canvas)
                fg?.apply {
                    setBounds(-pad, -pad, ICON_PX + pad, ICON_PX + pad)
                    draw(canvas)
                }
                return AppIcon(bitmap.asImageBitmap(), fullBleed = true)
            }
        }

        val bitmap = drawable.toBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        return AppIcon(bitmap.asImageBitmap(), fullBleed = hasOpaqueCorners(bitmap))
    }

    // Check if legacy icon has solid corners
    private fun hasOpaqueCorners(bitmap: Bitmap): Boolean {
        val m = 3
        val w = bitmap.width - 1 - m
        val h = bitmap.height - 1 - m
        return listOf(
            bitmap[m, m],
            bitmap[w, m],
            bitmap[m, h],
            bitmap[w, h],
        ).all { (it ushr 24) >= 240 }
    }

    @Suppress("DEPRECATION")
    private fun query(intent: Intent): List<ResolveInfo> = pm.queryIntentActivities(intent, 0)

    companion object {
        private const val ICON_PX = 192
        private const val PREFS_NAME = "playos"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_HIDDEN = "hidden_apps"
        private const val KEY_ORDER_APPS = "order_apps"
        private const val KEY_ORDER_MEDIA = "order_media"
        val DefaultAccent = Color(0xFF26335F)
    }
}

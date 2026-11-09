package com.archosoftware.home.playos.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
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
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.get
import androidx.core.graphics.scale
import java.util.concurrent.ConcurrentHashMap

enum class AppCategory(val label: String) {
    All("All"),
    Video("Video"),
    Music("Music"),
    Games("Games"),
    Tools("Tools"),
}

data class AppEntry(
    val label: String,
    val packageName: String,
    val category: AppCategory = AppCategory.Tools,
)

data class AppIcon(
    val bitmap: ImageBitmap,
    val fullBleed: Boolean,
)

data class AppDetails(
    val version: String?,
    val description: String?,
    val installed: Long,
    val updated: Long,
)

class AppRepository(private val context: Context) {

    private val pm = context.packageManager
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Cache ~34 icons at 192px (5MB budget)
    private val iconCache = object : LruCache<String, AppIcon>(5 * 1024) {
        override fun sizeOf(key: String, value: AppIcon): Int =
            (value.bitmap.width * value.bitmap.height * 4) / 1024
    }

    // Cache TV banners (320x180 px) - 8MB budget
    private val bannerCache = object : LruCache<String, ImageBitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            (value.width * value.height * 4) / 1024
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
            .map { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                val label = resolveInfo.loadLabel(pm).toString()
                val category = detectCategory(resolveInfo)
                AppEntry(label, pkg, category)
            }
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

    fun banner(packageName: String): ImageBitmap? {
        bannerCache[packageName]?.let { return it }
        return runCatching {
            val drawable = pm.getApplicationBanner(packageName)
                ?: pm.getActivityBanner(launchIntent(packageName) ?: return null)
            drawable?.toBitmap(BANNER_W, BANNER_H, Bitmap.Config.ARGB_8888)?.asImageBitmap()
        }.getOrNull()?.also { bannerCache.put(packageName, it) }
    }

    fun details(packageName: String): AppDetails? = runCatching {
        val pkgInfo = pm.getPackageInfo(packageName, 0)
        val appInfo = pkgInfo.applicationInfo
        val desc = runCatching { appInfo?.loadDescription(pm)?.toString() }.getOrNull()
        AppDetails(
            version = pkgInfo.versionName,
            description = desc?.takeIf { it.isNotBlank() },
            installed = pkgInfo.firstInstallTime,
            updated = pkgInfo.lastUpdateTime,
        )
    }.getOrNull()

    // Dominant accent color for background tinting and focus glow
    fun accent(packageName: String): Color {
        accentCache[packageName]?.let { return it }
        val source = banner(packageName)?.asAndroidBitmap()
            ?: icon(packageName)?.bitmap?.asAndroidBitmap()
            ?: return DefaultAccent

        val small = source.scale(16, 16, filter = true)
        var r = 0L
        var g = 0L
        var b = 0L
        var totalWeight = 0L
        for (y in 0 until small.height) {
            for (x in 0 until small.width) {
                val p = small[x, y]
                if ((p ushr 24) < 128) continue
                val pr = (p shr 16) and 0xFF
                val pg = (p shr 8) and 0xFF
                val pb = p and 0xFF
                val lum = (pr + pg + pb) / 3
                if (lum !in (20..245)) continue
                val maxC = maxOf(pr, maxOf(pg, pb))
                val minC = minOf(pr, minOf(pg, pb))
                val sat = maxC - minC
                val weight = ((sat * 2) + 10).toLong()
                r += pr * weight
                g += pg * weight
                b += pb * weight
                totalWeight += weight
            }
        }
        if (small !== source) small.recycle()

        val raw = if (totalWeight == 0L) {
            DefaultAccent
        } else {
            Color((r / totalWeight).toInt(), (g / totalWeight).toInt(), (b / totalWeight).toInt())
        }

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(
            android.graphics.Color.rgb((raw.red * 255).toInt(), (raw.green * 255).toInt(), (raw.blue * 255).toInt()),
            hsl,
        )
        if (hsl[2] < 0.35f) hsl[2] = 0.45f
        if (hsl[1] < 0.25f && hsl[1] > 0.05f) hsl[1] = 0.35f
        val result = Color(ColorUtils.HSLToColor(hsl))

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
        bannerCache.evictAll()
        accentCache.clear()
    }

    private fun detectCategory(resolveInfo: ResolveInfo): AppCategory {
        val appInfo = resolveInfo.activityInfo.applicationInfo
        val pkg = resolveInfo.activityInfo.packageName.lowercase()
        val label = runCatching { resolveInfo.loadLabel(pm).toString().lowercase() }.getOrDefault("")

        @Suppress("DEPRECATION")
        when (appInfo.category) {
            ApplicationInfo.CATEGORY_VIDEO -> return AppCategory.Video
            ApplicationInfo.CATEGORY_AUDIO -> return AppCategory.Music
            ApplicationInfo.CATEGORY_GAME -> return AppCategory.Games
            else -> Unit
        }

        @Suppress("DEPRECATION")
        if ((appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0) {
            return AppCategory.Games
        }

        return when {
            isVideo(pkg, label) -> AppCategory.Video
            isMusic(pkg, label) -> AppCategory.Music
            isGame(pkg, label) -> AppCategory.Games
            else -> AppCategory.Tools
        }
    }

    private fun isVideo(pkg: String, label: String): Boolean =
        pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("primevideo") ||
            pkg.contains("disney") || pkg.contains("vlc") || pkg.contains("kodi") ||
            pkg.contains("mxplayer") || pkg.contains("hulu") || pkg.contains("hbomax") ||
            pkg.contains("max") || pkg.contains("plex") || pkg.contains("twitch") ||
            pkg.contains("tv") || pkg.contains("video") || pkg.contains("cinema") ||
            label.contains("video") || label.contains("tv") || label.contains("movie") ||
            label.contains("player")

    private fun isMusic(pkg: String, label: String): Boolean =
        pkg.contains("spotify") || pkg.contains("music") || pkg.contains("audio") ||
            pkg.contains("soundcloud") || pkg.contains("pandora") || pkg.contains("deezer") ||
            pkg.contains("tidal") || pkg.contains("radio") || pkg.contains("podcast") ||
            label.contains("music") || label.contains("audio") || label.contains("radio")

    private fun isGame(pkg: String, label: String): Boolean =
        pkg.contains("game") || pkg.contains("steam") || pkg.contains("retroarch") ||
            pkg.contains("ppsspp") || pkg.contains("citra") || pkg.contains("moonlight") ||
            pkg.contains("playstation") || pkg.contains("xbox") || pkg.contains("arcade") ||
            pkg.contains("emulator") || label.contains("game")

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
        private const val BANNER_W = 320
        private const val BANNER_H = 180
        private const val PREFS_NAME = "playos"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_HIDDEN = "hidden_apps"
        private const val KEY_ORDER_APPS = "order_apps"
        private const val KEY_ORDER_MEDIA = "order_media"
        val DefaultAccent = Color(0xFF26335F)
    }
}

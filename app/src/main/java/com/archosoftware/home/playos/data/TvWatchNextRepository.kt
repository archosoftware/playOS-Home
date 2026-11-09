package com.archosoftware.home.playos.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WatchNextItem(
    val id: Long,
    val title: String,
    val subtitle: String,
    val posterArtUri: String? = null,
    val thumbnailUri: String? = null,
    val intentUri: String? = null,
    val packageName: String? = null,
    val playbackPositionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val progress: Float = 0f,
    val fallbackGradient: List<Long>? = null,
) {
    val displayImageUri: String?
        get() = thumbnailUri ?: posterArtUri
}

private const val PERMISSION_READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"

// Repository for querying Watch Next / Continue Watching programs from Android TV provider
class TvWatchNextRepository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val imageCache = LruCache<String, ImageBitmap>(30)

    private val _items = MutableStateFlow<List<WatchNextItem>>(emptyList())
    val items: StateFlow<List<WatchNextItem>> = _items.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        scope.launch {
            _items.value = queryWatchNext()
        }
    }

    private suspend fun queryWatchNext(): List<WatchNextItem> = withContext(Dispatchers.IO) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            PERMISSION_READ_TV_LISTINGS,
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return@withContext emptyList()
        }

        val results = mutableListOf<WatchNextItem>()
        runCatching {
            val projection = arrayOf(
                TvContract.WatchNextPrograms._ID,
                TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
                TvContract.WatchNextPrograms.COLUMN_TITLE,
                TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
                TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
                TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
                TvContract.WatchNextPrograms.COLUMN_SHORT_DESCRIPTION,
                TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
                TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI,
                TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
                TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
            )

            context.contentResolver.query(
                TvContract.WatchNextPrograms.CONTENT_URI,
                projection,
                null,
                null,
                "${TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS} DESC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms._ID)
                val pkgIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME)
                val titleIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_TITLE)
                val epTitleIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE)
                val seasonIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER)
                val epNumIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER)
                val descIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_SHORT_DESCRIPTION)
                val posterIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI)
                val thumbIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI)
                val intentIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_INTENT_URI)
                val posIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS)
                val durIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS)

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val title = (if (titleIdx >= 0) cursor.getString(titleIdx) else null) ?: continue
                    val pkg = if (pkgIdx >= 0) cursor.getString(pkgIdx) else null
                    val epTitle = if (epTitleIdx >= 0) cursor.getString(epTitleIdx) else null
                    val season = if (seasonIdx >= 0) cursor.getString(seasonIdx) else null
                    val episode = if (epNumIdx >= 0) cursor.getString(epNumIdx) else null
                    val desc = if (descIdx >= 0) cursor.getString(descIdx) else null
                    val poster = if (posterIdx >= 0) cursor.getString(posterIdx) else null
                    val thumb = if (thumbIdx >= 0) cursor.getString(thumbIdx) else null
                    val intent = if (intentIdx >= 0) cursor.getString(intentIdx) else null
                    val pos = if (posIdx >= 0) cursor.getLong(posIdx) else 0L
                    val dur = if (durIdx >= 0) cursor.getLong(durIdx) else 0L

                    val progress = if (dur > 0L) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
                    val subtitle = formatSubtitle(season, episode, epTitle, desc, pos, dur)

                    results.add(
                        WatchNextItem(
                            id = id,
                            title = title,
                            subtitle = subtitle,
                            posterArtUri = poster,
                            thumbnailUri = thumb,
                            intentUri = intent,
                            packageName = pkg,
                            playbackPositionMillis = pos,
                            durationMillis = dur,
                            progress = progress,
                        ),
                    )
                }
            }
        }
        results
    }

    suspend fun loadImage(item: WatchNextItem): ImageBitmap? = withContext(Dispatchers.IO) {
        val candidates = listOfNotNull(
            item.thumbnailUri,
            item.posterArtUri,
            if (item.id > 0L) "content://android.media.tv/watch_next_program/${item.id}" else null,
        )

        for (uriString in candidates) {
            imageCache[uriString]?.let { return@withContext it }
            val bitmap = fetchBitmap(uriString)
            if (bitmap != null) {
                val imageBitmap = bitmap.asImageBitmap()
                imageCache.put(uriString, imageBitmap)
                return@withContext imageBitmap
            }
        }
        null
    }

    suspend fun loadImage(uriString: String): ImageBitmap? = withContext(Dispatchers.IO) {
        imageCache[uriString]?.let { return@withContext it }
        val bitmap = fetchBitmap(uriString) ?: return@withContext null
        val imageBitmap = bitmap.asImageBitmap()
        imageCache.put(uriString, imageBitmap)
        imageBitmap
    }

    private fun fetchBitmap(uriString: String): android.graphics.Bitmap? = runCatching {
        val uri = uriString.toUri()
        val bytes = when (uri.scheme) {
            "http", "https" -> {
                val conn = (java.net.URL(uriString).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    instanceFollowRedirects = true
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android TV) AppleWebKit/537.36",
                    )
                }
                conn.inputStream.use { it.readBytes() }
            }
            else -> {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
        } ?: return@runCatching null

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        options.inSampleSize = calculateInSampleSize(options, 512, 288)
        options.inJustDecodeBounds = false
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }.getOrNull()

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun launch(item: WatchNextItem) {
        val intentUri = item.intentUri
        if (!intentUri.isNullOrBlank()) {
            val intent = runCatching {
                if (intentUri.startsWith("intent:")) {
                    Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
                } else {
                    Intent(Intent.ACTION_VIEW, intentUri.toUri())
                }
            }.getOrNull()

            if (intent != null) {
                if (!item.packageName.isNullOrBlank()) {
                    intent.`package` = item.packageName
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val started = runCatching { context.startActivity(intent) }.isSuccess
                if (started) return
            }
        }

        val pkg = item.packageName
        if (!pkg.isNullOrBlank()) {
            val pm = context.packageManager
            val launchIntent = pm.getLeanbackLaunchIntentForPackage(pkg)
                ?: pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val started = runCatching { context.startActivity(launchIntent) }.isSuccess
                if (started) return
            }
        }

        // Search YouTube fallback
        try {
            val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube.tv")
                putExtra("query", item.title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(searchIntent)
        } catch (_: Exception) {
            try {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    "https://www.youtube.com/results?search_query=${Uri.encode(item.title)}".toUri(),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(webIntent)
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    private fun formatSubtitle(
        season: String?,
        episode: String?,
        epTitle: String?,
        desc: String?,
        pos: Long,
        dur: Long,
    ): String {
        if (!season.isNullOrEmpty() && !episode.isNullOrEmpty()) {
            val epPrefix = "S$season E$episode"
            return if (!epTitle.isNullOrEmpty()) "$epPrefix • $epTitle" else epPrefix
        }
        if (!epTitle.isNullOrEmpty()) return epTitle

        if ((dur > 0L) && (pos > 0L)) {
            val remaining = (dur - pos).coerceAtLeast(0L)
            val hours = remaining / 3600_000L
            val mins = (remaining % 3600_000L) / 60_000L
            return when {
                hours > 0L -> "${hours}h ${mins}m left"
                mins > 0L -> "${mins}m left"
                else -> "Finishing up"
            }
        }

        return desc ?: ""
    }
}

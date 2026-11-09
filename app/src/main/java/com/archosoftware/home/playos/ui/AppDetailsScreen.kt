@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.archosoftware.home.playos.ui

import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.archosoftware.home.playos.data.AppDetails
import com.archosoftware.home.playos.data.AppEntry
import com.archosoftware.home.playos.data.AppIcon
import com.archosoftware.home.playos.data.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

private sealed interface DetailArt {
    class Banner(val bitmap: ImageBitmap) : DetailArt
    class Glyph(val icon: AppIcon?) : DetailArt
}

// AppDetailsView Screen
@Composable
internal fun AppDetailsScreen(
    app: AppEntry,
    repository: AppRepository,
    isPinned: Boolean,
    openRequester: FocusRequester,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onAppInfo: () -> Unit,
) {
    val context = LocalContext.current

    val details by produceState<AppDetails?>(initialValue = null, app.packageName) {
        value = withContext(Dispatchers.Default) { repository.details(app.packageName) }
    }
    val art by produceState<DetailArt?>(initialValue = null, app.packageName) {
        value = withContext(Dispatchers.Default) {
            repository.banner(app.packageName)?.let { DetailArt.Banner(it) }
                ?: DetailArt.Glyph(repository.icon(app.packageName))
        }
    }
    val accentTarget by produceState(AppRepository.DefaultAccent, app.packageName) {
        value = withContext(Dispatchers.Default) { repository.accent(app.packageName) }
    }

    val accent = animateColorAsState(accentTarget, tween(400), label = "detailsAccent")

    val subtitle = remember(app, details) {
        listOfNotNull(app.category.label, details?.version?.let { "Version $it" })
            .joinToString("  •  ")
    }
    val description = remember(details) {
        val (_, desc, installed, updated) = details ?: return@remember null
        desc ?: run {
            val fmt = DateFormat.getMediumDateFormat(context)
            "Installed ${fmt.format(Date(installed))}  •  Updated ${fmt.format(Date(updated))}"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            lerp(PlayColors.Base, accent.value, 0.50f),
                            lerp(PlayColors.Base, accent.value, 0.14f),
                            PlayColors.Base,
                        ),
                    ),
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ScreenPadding, vertical = 28.dp),
        ) {
            PageHeader(title = "App details", onBack = onBack)

            Spacer(Modifier.height(36.dp))

            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        (art as? DetailArt.Glyph)?.icon?.let { (bitmap, fullBleed) ->
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                contentScale = if (fullBleed) ContentScale.Crop else ContentScale.Fit,
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(RoundedCornerShape(20.dp)),
                            )
                            Spacer(Modifier.width(20.dp))
                        }
                        Column {
                            Text(
                                text = app.label,
                                color = Color.White,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Light,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 420.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = subtitle,
                                color = PlayColors.TextDim,
                                fontSize = 15.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    val sounds = LocalTvSounds.current

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DetailButton(
                            text = "Open",
                            icon = Icons.Default.PlayArrow,
                            primary = true,
                            onClick = onOpen,
                            modifier = Modifier
                                .focusRequester(openRequester)
                                .detailButtonNavSounds(sounds, canGoLeft = false, canGoRight = true),
                        )
                        DetailButton(
                            text = if (isPinned) "Unpin from home" else "Pin to home",
                            icon = if (isPinned) Icons.Default.Star else Icons.Default.StarBorder,
                            primary = false,
                            onClick = onTogglePin,
                            modifier = Modifier.detailButtonNavSounds(sounds, canGoLeft = true, canGoRight = true),
                        )
                        DetailButton(
                            text = "App info",
                            icon = Icons.Default.Info,
                            primary = false,
                            onClick = onAppInfo,
                            modifier = Modifier.detailButtonNavSounds(sounds, canGoLeft = true, canGoRight = false),
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    description?.let {
                        Text(
                            text = it,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 15.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 460.dp),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(width = 300.dp, height = 170.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center,
                ) {
                    when (val current = art) {
                        is DetailArt.Banner -> Image(
                            bitmap = current.bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )

                        is DetailArt.Glyph -> current.icon?.let { (bitmap, fullBleed) ->
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                contentScale = if (fullBleed) ContentScale.Crop else ContentScale.Fit,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(26.dp)),
                            )
                        }

                        null -> Unit
                    }
                }
            }
        }
    }
}

private fun Modifier.detailButtonNavSounds(
    sounds: TvSounds,
    canGoLeft: Boolean,
    canGoRight: Boolean,
): Modifier = this.onKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
    when (event.key) {
        Key.DirectionLeft -> {
            if (canGoLeft) {
                sounds.playNavLeft()
                false
            } else {
                true
            }
        }
        Key.DirectionRight -> {
            if (canGoRight) {
                sounds.playNavRight()
                false
            } else {
                true
            }
        }
        Key.DirectionUp -> {
            sounds.playNavUp()
            false
        }
        Key.DirectionDown -> true
        else -> false
    }
}

@Composable
private fun DetailButton(
    text: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sounds = LocalTvSounds.current
    Surface(
        onClick = {
            sounds.playClick()
            onClick()
        },
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) PlayColors.Accent else Color.White.copy(alpha = 0.12f),
            contentColor = if (primary) PlayColors.OnAccent else Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = PlayColors.OnAccent,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, PlayColors.Accent), shape = CircleShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

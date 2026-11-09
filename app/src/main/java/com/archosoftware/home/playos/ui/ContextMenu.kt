@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.archosoftware.home.playos.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.archosoftware.home.playos.data.AppEntry
import com.archosoftware.home.playos.data.AppIcon
import com.archosoftware.home.playos.data.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ActionMenu(
    app: AppEntry,
    repository: AppRepository,
    isPinned: Boolean,
    showReorder: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onMove: () -> Unit,
    onSortAZ: () -> Unit,
    onSortZA: () -> Unit,
    onAppInfo: () -> Unit,
    onHide: () -> Unit,
    onForceStop: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val appear = remember { Animatable(0f) }

    val icon by produceState<AppIcon?>(initialValue = null, app.packageName) {
        value = withContext(Dispatchers.Default) { repository.icon(app.packageName) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LaunchedEffect(Unit) {
            launch { appear.animateTo(1f, tween(160)) }
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }

        var hasValidKeyDown by remember { mutableStateOf(value = false) }

        Row(
            modifier = Modifier
                .onPreviewKeyEvent { event ->
                    val isSelectKey = (event.key == Key.DirectionCenter) ||
                        (event.key == Key.Enter) ||
                        (event.key == Key.NumPadEnter)
                    if (!isSelectKey) return@onPreviewKeyEvent false

                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (event.nativeKeyEvent.repeatCount == 0) {
                                hasValidKeyDown = true
                                false
                            } else {
                                !hasValidKeyDown
                            }
                        }
                        KeyEventType.KeyUp -> {
                            if (!hasValidKeyDown) {
                                true
                            } else {
                                hasValidKeyDown = false
                                false
                            }
                        }
                        else -> false
                    }
                }
                .graphicsLayer {
                    alpha = appear.value
                    val s = 0.94f + (0.06f * appear.value)
                    scaleX = s
                    scaleY = s
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(PlayColors.Tile),
                contentAlignment = Alignment.Center,
            ) {
                icon?.let { (bitmap, fullBleed) ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = if (fullBleed) ContentScale.Crop else ContentScale.Fit,
                        modifier = Modifier
                            .size(if (fullBleed) 132.dp else 84.dp)
                            .clip(RoundedCornerShape(if (fullBleed) 26.dp else 16.dp)),
                    )
                }
            }

            Spacer(Modifier.width(24.dp))

            Column(
                modifier = Modifier
                    .width(290.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(PlayColors.Card)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                Text(
                    text = app.label,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )

                val sounds = LocalTvSounds.current

                MenuItem(
                    text = "Open",
                    icon = Icons.Default.PlayArrow,
                    onClick = onOpen,
                    modifier = Modifier
                        .focusRequester(first)
                        .menuItemNavSounds(sounds, canGoUp = false, canGoDown = true),
                )
                MenuItem(
                    text = if (isPinned) "Unpin from home" else "Pin to home",
                    icon = if (isPinned) Icons.Default.Star else Icons.Default.StarBorder,
                    onClick = onTogglePin,
                    modifier = Modifier.menuItemNavSounds(sounds, canGoUp = true, canGoDown = true),
                )
                if (showReorder) {
                    MenuItem(
                        text = "Move",
                        icon = Icons.Default.OpenWith,
                        onClick = onMove,
                        modifier = Modifier.menuItemNavSounds(sounds, canGoUp = true, canGoDown = true),
                    )
                    MenuItem(
                        text = "Sort A-Z",
                        icon = Icons.Default.SortByAlpha,
                        onClick = onSortAZ,
                        modifier = Modifier.menuItemNavSounds(sounds, canGoUp = true, canGoDown = true),
                    )
                    MenuItem(
                        text = "Sort Z-A",
                        icon = Icons.Default.SortByAlpha,
                        onClick = onSortZA,
                        modifier = Modifier.menuItemNavSounds(sounds, canGoUp = true, canGoDown = true),
                    )
                }
                MenuItem(
                    text = "App info",
                    icon = Icons.Default.Info,
                    onClick = onAppInfo,
                    modifier = Modifier.menuItemNavSounds(sounds, canGoUp = true, canGoDown = true),
                )
                MenuItem(
                    text = "Hide from launcher",
                    icon = Icons.Default.VisibilityOff,
                    onClick = onHide,
                    modifier = Modifier.menuItemNavSounds(sounds, canGoUp = true, canGoDown = true),
                )
                MenuItem(
                    text = "Force stop",
                    icon = Icons.Default.Close,
                    onClick = onForceStop,
                    modifier = Modifier.menuItemNavSounds(sounds, canGoUp = true, canGoDown = false),
                )
            }
        }
    }
}

private fun Modifier.menuItemNavSounds(
    sounds: TvSounds,
    canGoUp: Boolean,
    canGoDown: Boolean,
): Modifier = this.onKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
    when (event.key) {
        Key.DirectionUp -> {
            if (canGoUp) {
                sounds.playNavUp()
                false
            } else {
                true
            }
        }
        Key.DirectionDown -> {
            if (canGoDown) {
                sounds.playNavDown()
                false
            } else {
                true
            }
        }
        Key.DirectionLeft, Key.DirectionRight -> true
        else -> false
    }
}

@Composable
private fun MenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sounds = LocalTvSounds.current
    Surface(
        onClick = {
            sounds.playClick()
            onClick()
        },
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = PlayColors.Accent,
            focusedContentColor = PlayColors.OnAccent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text(text = text, fontSize = 16.sp)
        }
    }
}

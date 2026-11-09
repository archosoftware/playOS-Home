@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.archosoftware.home.playos.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
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
import com.archosoftware.home.playos.data.AppCategory
import com.archosoftware.home.playos.data.AppEntry
import com.archosoftware.home.playos.data.AppIcon
import com.archosoftware.home.playos.data.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

private const val GRID_LONG_PRESS_MS = 600L
private val GridTileShape = RoundedCornerShape(14.dp)

// App drawer
@Composable
internal fun AllAppsScreen(
    apps: List<AppEntry>,
    favorites: Set<String>,
    repository: AppRepository,
    firstItemRequester: FocusRequester,
    onBack: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
) {
    var category by remember { mutableStateOf(AppCategory.All) }
    val shown = remember(apps, category) {
        if (category == AppCategory.All) apps else apps.filter { (_, _, cat) -> cat == category }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0C1330), PlayColors.Base))),
    ) {
        PageHeader(
            title = "All apps",
            onBack = onBack,
            modifier = Modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 28.dp),
        )

        Spacer(Modifier.height(18.dp))

        val sounds = LocalTvSounds.current

        Row(
            modifier = Modifier.padding(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for ((entryIndex, entry) in AppCategory.entries.withIndex()) {
                CategoryChip(
                    text = entry.label,
                    selected = entry == category,
                    onClick = { category = entry },
                    modifier = Modifier.onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> {
                                if (entryIndex > 0) {
                                    sounds.playNavLeft()
                                    false
                                } else {
                                    true
                                }
                            }
                            Key.DirectionRight -> {
                                if (entryIndex < AppCategory.entries.lastIndex) {
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
                            Key.DirectionDown -> {
                                sounds.playNavDown()
                                false
                            }
                            else -> false
                        }
                    },
                )
            }
        }

        if (shown.isEmpty()) {
            Text(
                text = "No apps in this category",
                color = PlayColors.TextDim,
                fontSize = 18.sp,
                modifier = Modifier.padding(start = ScreenPadding, top = 32.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    top = 20.dp,
                    bottom = 40.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(shown, key = { _, (_, packageName) -> packageName }) { index, app ->
                    AppGridTile(
                        index = index,
                        totalCount = shown.size,
                        app = app,
                        repository = repository,
                        pinned = app.packageName in favorites,
                        onClick = { onLaunch(app) },
                        onLongPress = { onLongPress(app) },
                        modifier = if (index == 0) Modifier.focusRequester(firstItemRequester) else Modifier,
                    )
                }
            }
        }
    }
}

/** Back arrow and title, shared by All apps and App details. */
@Composable
internal fun PageHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val sounds = LocalTvSounds.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = {
                sounds.playClick()
                onBack()
            },
            modifier = Modifier
                .size(44.dp)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            sounds.playNavDown()
                            false
                        }
                        Key.DirectionLeft, Key.DirectionUp -> true
                        else -> false
                    }
                },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.White.copy(alpha = 0.18f),
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = CircleShape),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(24.dp),
                    tint = Color.White,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CategoryChip(
    text: String,
    selected: Boolean,
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
            containerColor = if (selected) PlayColors.Accent else Color.White.copy(alpha = 0.10f),
            contentColor = if (selected) PlayColors.OnAccent else Color.White,
            focusedContainerColor = if (selected) PlayColors.Accent else Color.White.copy(alpha = 0.22f),
            focusedContentColor = if (selected) PlayColors.OnAccent else Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = CircleShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

/**
 * One grid tile. Not a clickable Surface: OK launches on release, and a 600 ms
 * hold opens the menu, using the same timer as the Home row, because many TV
 * remotes don't send repeat events for a held OK button.
 */
@Composable
private fun AppGridTile(
    index: Int,
    totalCount: Int,
    app: AppEntry,
    repository: AppRepository,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sounds = LocalTvSounds.current
    var focused by remember { mutableStateOf(value = false) }
    val focus by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(140),
        label = "gridFocus",
    )

    val icon by produceState<AppIcon?>(initialValue = null, app.packageName) {
        value = withContext(Dispatchers.Default) { repository.icon(app.packageName) }
    }

    val scope = rememberCoroutineScope()
    var pressJob by remember { mutableStateOf<Job?>(null) }
    var longPressFired by remember { mutableStateOf(value = false) }
    DisposableEffect(Unit) {
        onDispose { pressJob?.cancel() }
    }

    val textColor = if (focused) PlayColors.OnAccent else Color.White

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) {
                    pressJob?.cancel()
                    pressJob = null
                }
            }
            .onKeyEvent { event ->
                val down = event.type == KeyEventType.KeyDown
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (down) {
                            if ((index % 4) != 0) sounds.playNavLeft()
                            (index % 4) == 0
                        } else false
                    }

                    Key.DirectionRight -> {
                        if (down) {
                            val atEnd = ((index % 4) == 3) || ((index + 1) >= totalCount)
                            if (!atEnd) sounds.playNavRight()
                            atEnd
                        } else false
                    }

                    Key.DirectionUp -> {
                        if (down) sounds.playNavUp()
                        false
                    }

                    Key.DirectionDown -> {
                        if (down) {
                            val atBottom = (index + 4) >= totalCount
                            if (!atBottom) sounds.playNavDown()
                            atBottom
                        } else false
                    }

                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        when (event.type) {
                            KeyEventType.KeyDown -> {
                                if (event.nativeKeyEvent.repeatCount == 0) {
                                    pressJob?.cancel()
                                    longPressFired = false
                                    pressJob = scope.launch {
                                        delay(GRID_LONG_PRESS_MS.milliseconds)
                                        longPressFired = true
                                        sounds.playClick()
                                        onLongPress()
                                    }
                                }
                            }

                            KeyEventType.KeyUp -> {
                                pressJob?.cancel()
                                pressJob = null
                                if (!longPressFired) {
                                    sounds.playClick()
                                    onClick()
                                }
                                longPressFired = false
                            }

                            else -> Unit
                        }
                        true
                    }

                    else -> false
                }
            }
            .focusable()
            .graphicsLayer {
                val scale = 1f + (0.05f * focus)
                scaleX = scale
                scaleY = scale
            }
            .clip(GridTileShape)
            .drawBehind {
                drawRect(lerp(PlayColors.Tile, PlayColors.FocusFill, focus))
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { (bitmap, fullBleed) ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = if (fullBleed) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        } ?: Spacer(Modifier.size(38.dp))

        Spacer(Modifier.width(14.dp))

        Text(
            text = app.label,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (pinned) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Pinned",
                modifier = Modifier.size(16.dp),
                tint = if (focused) PlayColors.OnAccent else PlayColors.Favorite,
            )
        }
    }
}

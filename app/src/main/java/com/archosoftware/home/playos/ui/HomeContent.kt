@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.archosoftware.home.playos.ui

import android.content.Context
import android.graphics.BlurMaskFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.archosoftware.home.playos.R
import com.archosoftware.home.playos.data.AppEntry
import com.archosoftware.home.playos.data.AppIcon
import com.archosoftware.home.playos.data.AppRepository
import com.archosoftware.home.playos.data.TvWatchNextRepository
import com.archosoftware.home.playos.data.WatchNextItem
import com.archosoftware.home.playos.data.openSystemScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds

private const val LONG_PRESS_MS = 600L

private val ShortcutW = 132.dp
private val ShortcutH = 80.dp
private val ShortcutGap = 14.dp
private val ShortcutShape = RoundedCornerShape(14.dp)

private class PressState {
    var job: Job? = null
    var fired = false
}

private sealed interface TileArt {
    class Banner(val bitmap: ImageBitmap) : TileArt
    class Glyph(val icon: AppIcon?) : TileArt
}

@Composable
internal fun HomeContent(
    pinned: List<AppEntry>,
    repository: AppRepository,
    watchNextRepository: TvWatchNextRepository,
    selectedIndex: MutableIntState,
    isMoving: Boolean,
    hasHiddenApps: Boolean,
    rowRequester: FocusRequester,
    searchRequester: FocusRequester,
    clockRequester: FocusRequester,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onOpenAllApps: () -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onFinishMove: () -> Unit,
    onCancelMove: () -> Unit,
    onSearch: () -> Unit,
    onOpenHidden: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenQuick: () -> Unit,
) {
    val homePill = remember { FocusRequester() }
    val continueWatching by watchNextRepository.items.collectAsState()
    val cwRequester = remember { FocusRequester() }
    val cwIndex = remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        SceneBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                hasHiddenApps = hasHiddenApps,
                homePillRequester = homePill,
                searchRequester = searchRequester,
                clockRequester = clockRequester,
                onHome = { runCatching { rowRequester.requestFocus() } },
                onOpenAllApps = onOpenAllApps,
                onOpenSettings = onOpenSettings,
                onSearch = onSearch,
                onOpenHidden = onOpenHidden,
                onOpenQuick = onOpenQuick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = ScreenPadding, end = ScreenPadding, top = 22.dp),
            )

            Spacer(Modifier.weight(0.1f))

            Text(
                text = "Your apps",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = ScreenPadding),
            )

            ShortcutRow(
                pinned = pinned,
                repository = repository,
                selectedIndex = selectedIndex,
                isMoving = isMoving,
                canGoDown = continueWatching.isNotEmpty(),
                rowRequester = rowRequester,
                onLaunch = onLaunch,
                onLongPress = onLongPress,
                onOpenAllApps = onOpenAllApps,
                onMoveItem = onMoveItem,
                onFinishMove = onFinishMove,
                onCancelMove = onCancelMove,
                modifier = Modifier
                    .focusProperties {
                        up = homePill
                        down = if (continueWatching.isNotEmpty()) cwRequester else FocusRequester.Default
                    },
            )

            if (isMoving || pinned.isEmpty()) {
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .padding(start = ScreenPadding),
                ) {
                    when {
                        isMoving -> Text(
                            text = "Left / Right to move  •  OK to place  •  Back to cancel",
                            color = PlayColors.Favorite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )

                        pinned.isEmpty() -> Text(
                            text = "Nothing pinned yet. Open All apps, hold OK on an app and choose Pin to home.",
                            color = PlayColors.TextDim,
                            fontSize = 14.sp,
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }

            if (continueWatching.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Continue watching",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = ScreenPadding),
                )

                ContinueWatchingRow(
                    items = continueWatching,
                    repository = watchNextRepository,
                    selectedIndex = cwIndex,
                    cwRequester = cwRequester,
                    onLaunch = { item -> watchNextRepository.launch(item) },
                    modifier = Modifier
                        .focusProperties { up = rowRequester },
                )
            }

            Spacer(Modifier.weight(0.5f))
        }
    }
}

// Background
@Composable
private fun SceneBackground(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.wallpaper),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color(0xAA04060F), Color.Transparent)),
            ),
        )
    }
}

@Composable
private fun TopBar(
    hasHiddenApps: Boolean,
    homePillRequester: FocusRequester,
    searchRequester: FocusRequester,
    clockRequester: FocusRequester,
    onHome: () -> Unit,
    onOpenAllApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onSearch: () -> Unit,
    onOpenHidden: () -> Unit,
    onOpenQuick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sounds = LocalTvSounds.current

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(id = R.drawable.playos_logo),
            contentDescription = "playOS",
            modifier = Modifier
                .height(20.dp)
                .alpha(0.5f),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.width(36.dp))

        NavPill(
            text = "Home",
            selected = true,
            onClick = onHome,
            modifier = Modifier
                .focusRequester(homePillRequester)
                .topBarNavSounds(sounds, canGoLeft = false, canGoRight = true),
        )
        Spacer(Modifier.width(6.dp))
        NavPill(
            text = "Apps",
            selected = false,
            onClick = onOpenAllApps,
            modifier = Modifier.topBarNavSounds(sounds, canGoLeft = true, canGoRight = true),
        )

        Spacer(Modifier.weight(1f))

        if (hasHiddenApps) {
            TopIconButton(
                onClick = onOpenHidden,
                modifier = Modifier.topBarNavSounds(sounds, canGoLeft = true, canGoRight = true),
            ) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = "Hidden apps",
                    modifier = Modifier.size(24.dp),
                    tint = Color.White,
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        TopIconButton(
            onClick = onSearch,
            modifier = Modifier
                .focusRequester(searchRequester)
                .topBarNavSounds(sounds, canGoLeft = true, canGoRight = true),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                modifier = Modifier.size(24.dp),
                tint = Color.White,
            )
        }
        Spacer(Modifier.width(8.dp))
        NetworkButton(
            modifier = Modifier.topBarNavSounds(sounds, canGoLeft = true, canGoRight = true),
        )
        Spacer(Modifier.width(8.dp))
        TopIconButton(
            onClick = onOpenSettings,
            modifier = Modifier.topBarNavSounds(sounds, canGoLeft = true, canGoRight = true),
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                modifier = Modifier.size(24.dp),
                tint = Color.White,
            )
        }
        Spacer(Modifier.width(8.dp))
        ClockButton(
            onClick = onOpenQuick,
            modifier = Modifier
                .focusRequester(clockRequester)
                .topBarNavSounds(sounds, canGoLeft = true, canGoRight = false),
        )
    }
}

private fun Modifier.topBarNavSounds(
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
        Key.DirectionDown -> {
            sounds.playNavDown()
            false
        }
        Key.DirectionUp -> true
        else -> false
    }
}

@Composable
private fun NavPill(
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
            containerColor = if (selected) PlayColors.Accent else Color.Transparent,
            contentColor = if (selected) PlayColors.OnAccent else Color.White,
            focusedContainerColor = if (selected) PlayColors.Accent else Color.White.copy(alpha = 0.18f),
            focusedContentColor = if (selected) PlayColors.OnAccent else Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = CircleShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun TopIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val sounds = LocalTvSounds.current
    Surface(
        onClick = {
            sounds.playClick()
            onClick()
        },
        modifier = modifier.size(44.dp),
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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
    }
}

private enum class NetworkKind { Wifi, Ethernet, None }

private fun networkKindOf(capabilities: NetworkCapabilities?): NetworkKind = when {
    capabilities == null -> NetworkKind.None
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.Ethernet
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.Wifi
    else -> NetworkKind.None
}

private fun currentNetworkKind(manager: ConnectivityManager?): NetworkKind {
    if (manager == null) return NetworkKind.None
    return runCatching { networkKindOf(manager.getNetworkCapabilities(manager.activeNetwork)) }
        .getOrDefault(NetworkKind.None)
}

@Composable
private fun rememberNetworkKind(): NetworkKind {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }
    var kind by remember { mutableStateOf(currentNetworkKind(manager)) }

    DisposableEffect(manager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                kind = networkKindOf(capabilities)
            }

            override fun onLost(network: Network) {
                kind = currentNetworkKind(manager)
            }
        }
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager?.unregisterNetworkCallback(callback) } }
    }

    return kind
}

@Composable
private fun NetworkButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val kind = rememberNetworkKind()

    TopIconButton(
        onClick = { context.openSystemScreen(Settings.ACTION_WIFI_SETTINGS) },
        modifier = modifier,
    ) {
        Icon(
            imageVector = when (kind) {
                NetworkKind.Wifi -> Icons.Default.Wifi
                NetworkKind.Ethernet -> Icons.Default.SettingsEthernet
                NetworkKind.None -> Icons.Default.WifiOff
            },
            contentDescription = when (kind) {
                NetworkKind.Wifi -> "Wi-Fi connected"
                NetworkKind.Ethernet -> "Ethernet connected"
                NetworkKind.None -> "No network"
            },
            modifier = Modifier.size(24.dp),
            tint = if (kind == NetworkKind.None) Color.White.copy(alpha = 0.5f) else Color.White,
        )
    }
}

@Composable
private fun ClockButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sounds = LocalTvSounds.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay((60_000L - (now % 60_000L)).milliseconds)
        }
    }

    val text = remember(now) { DateFormat.getTimeFormat(context).format(Date(now)) }

    Surface(
        onClick = {
            sounds.playClick()
            onClick()
        },
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.18f),
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = CircleShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun ShortcutRow(
    pinned: List<AppEntry>,
    repository: AppRepository,
    selectedIndex: MutableIntState,
    isMoving: Boolean,
    canGoDown: Boolean,
    rowRequester: FocusRequester,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onOpenAllApps: () -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onFinishMove: () -> Unit,
    onCancelMove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allAppsIndex = pinned.size
    val sounds = LocalTvSounds.current
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selectedIndex.intValue - 2).coerceAtLeast(0),
    )
    var rowFocused by remember { mutableStateOf(value = false) }
    val press = remember { PressState() }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { press.job?.cancel() }
    }

    // Keep selected item within the visible viewport without unnecessary scrolling
    LaunchedEffect(listState) {
        snapshotFlow { selectedIndex.intValue }.collectLatest { selected ->
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == selected }
            if (item == null) {
                listState.animateScrollToItem(selected)
            } else {
                val start = info.viewportStartOffset + info.beforeContentPadding
                val end = info.viewportEndOffset - info.afterContentPadding
                val itemEnd = item.offset + item.size
                when {
                    itemEnd > end -> listState.animateScrollBy((itemEnd - end).toFloat(), tween(200))
                    item.offset < start -> listState.animateScrollBy((item.offset - start).toFloat(), tween(200))
                }
            }
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(ShortcutH + 40.dp)
            .focusRequester(rowRequester)
            .onFocusChanged {
                rowFocused = it.isFocused
                if (!it.isFocused) {
                    press.job?.cancel()
                    press.job = null
                }
            }
            .onKeyEvent { event ->
                val selected = selectedIndex.intValue
                val down = event.type == KeyEventType.KeyDown
                val up = event.type == KeyEventType.KeyUp

                when (event.key) {
                    Key.DirectionLeft -> {
                        if (down) {
                            if (isMoving) {
                                if (selected > 0) {
                                    sounds.playNavLeft()
                                    onMoveItem(selected, selected - 1)
                                }
                            } else {
                                if (selected > 0) {
                                    sounds.playNavLeft()
                                    selectedIndex.intValue = selected - 1
                                }
                            }
                        }
                        true
                    }

                    Key.DirectionRight -> {
                        if (down) {
                            if (isMoving) {
                                if (selected < pinned.lastIndex) {
                                    sounds.playNavRight()
                                    onMoveItem(selected, selected + 1)
                                }
                            } else {
                                if (selected < allAppsIndex) {
                                    sounds.playNavRight()
                                    selectedIndex.intValue = selected + 1
                                }
                            }
                        }
                        true
                    }

                    Key.DirectionUp -> {
                        if (isMoving) {
                            true
                        } else {
                            if (down) {
                                sounds.playNavUp()
                            }
                            false
                        }
                    }

                    Key.DirectionDown -> {
                        if (isMoving || !canGoDown) {
                            true
                        } else {
                            if (down) {
                                sounds.playNavDown()
                            }
                            false
                        }
                    }

                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (isMoving) {
                            if (up) {
                                sounds.playClick()
                                onFinishMove()
                            }
                        } else if (down) {
                            // Ignore remote key-repeat events; start long-press timer on first KeyDown
                            if (event.nativeKeyEvent.repeatCount == 0) {
                                press.job?.cancel()
                                press.fired = false
                                val app = pinned.getOrNull(selected)
                                if (app != null) {
                                    press.job = scope.launch {
                                        delay(LONG_PRESS_MS.milliseconds)
                                        press.fired = true
                                        sounds.playClick()
                                        onLongPress(app)
                                    }
                                }
                            }
                        } else if (up) {
                            press.job?.cancel()
                            press.job = null
                            if (!press.fired) {
                                sounds.playClick()
                                val app = pinned.getOrNull(selected)
                                if (app != null) onLaunch(app) else onOpenAllApps()
                            }
                            press.fired = false
                        }
                        true
                    }

                    Key.Back, Key.Escape -> {
                        if (isMoving) {
                            if (up) {
                                sounds.playClick()
                                onCancelMove()
                            }
                            true
                        } else {
                            false
                        }
                    }

                    else -> false
                }
            }
            .focusable(),
        contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(ShortcutGap),
        verticalAlignment = Alignment.CenterVertically,
        userScrollEnabled = false,
    ) {
        itemsIndexed(pinned, key = { _, (_, packageName) -> packageName }) { index, app ->
            val isSelected = selectedIndex.intValue == index
            ShortcutTile(
                app = app,
                repository = repository,
                selected = rowFocused && isSelected,
                moving = isMoving && isSelected,
            )
        }
        item(key = "all_apps") {
            AllAppsTile(selected = rowFocused && (selectedIndex.intValue == allAppsIndex))
        }
    }
}

private val cardShadowPaint = android.graphics.Paint().apply {
    isAntiAlias = true
    style = android.graphics.Paint.Style.FILL
}

@Composable
private fun TileFrame(
    selected: Boolean,
    moving: Boolean,
    background: Color,
    glowColor: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit,
) {
    val focus by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(140),
        label = "tileFocus",
    )

    val density = LocalDensity.current
    val baseBlur = remember(density) {
        BlurMaskFilter(density.run { 7.dp.toPx() }, BlurMaskFilter.Blur.NORMAL)
    }
    val glowBlur = remember(density) {
        BlurMaskFilter(density.run { 10.dp.toPx() }, BlurMaskFilter.Blur.NORMAL)
    }

    Box(
        modifier = Modifier
            .zIndex(if (selected) 1f else 0f)
            .size(width = ShortcutW, height = ShortcutH)
            .graphicsLayer {
                val scale = 1f + (0.12f * focus)
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                val cornerRadiusPx = 14.dp.toPx()
                val offsetY = 3.dp.toPx()

                drawIntoCanvas { canvas ->
                    cardShadowPaint.color = android.graphics.Color.argb(90, 0, 0, 0)
                    cardShadowPaint.maskFilter = baseBlur
                    canvas.nativeCanvas.drawRoundRect(
                        0f,
                        offsetY,
                        size.width,
                        size.height + offsetY,
                        cornerRadiusPx,
                        cornerRadiusPx,
                        cardShadowPaint,
                    )

                    if ((focus > 0.01f) && (glowColor != Color.Transparent)) {
                        val spread = 2.dp.toPx()
                        val glowAlpha = (glowColor.alpha * 0.65f * focus * 255).toInt().coerceIn(0, 255)
                        cardShadowPaint.color = android.graphics.Color.argb(
                            glowAlpha,
                            (glowColor.red * 255).toInt(),
                            (glowColor.green * 255).toInt(),
                            (glowColor.blue * 255).toInt(),
                        )
                        cardShadowPaint.maskFilter = glowBlur
                        canvas.nativeCanvas.drawRoundRect(
                            -spread,
                            -spread + offsetY + 1.dp.toPx(),
                            size.width + spread,
                            size.height + spread + offsetY + 1.dp.toPx(),
                            cornerRadiusPx + spread,
                            cornerRadiusPx + spread,
                            cardShadowPaint,
                        )
                    }
                }
            }
            .clip(ShortcutShape)
            .background(background)
            .drawWithContent {
                drawContent()
                if (moving) {
                    drawRing(PlayColors.Favorite, 3.5.dp.toPx(), 14.dp.toPx())
                } else if (focus > 0.01f) {
                    drawRing(Color.White.copy(alpha = focus), 3.dp.toPx(), 14.dp.toPx())
                }
            },
        content = content,
    )
}

private fun DrawScope.drawRing(color: Color, widthPx: Float, radiusPx: Float) {
    drawRoundRect(
        color = color,
        topLeft = Offset(widthPx / 2f, widthPx / 2f),
        size = Size(size.width - widthPx, size.height - widthPx),
        cornerRadius = CornerRadius(radiusPx - (widthPx / 2f)),
        style = Stroke(widthPx),
    )
}

@Composable
private fun ShortcutTile(
    app: AppEntry,
    repository: AppRepository,
    selected: Boolean,
    moving: Boolean,
) {
    val art by produceState<TileArt?>(initialValue = null, app.packageName) {
        value = withContext(Dispatchers.Default) {
            repository.banner(app.packageName)?.let { TileArt.Banner(it) }
                ?: TileArt.Glyph(repository.icon(app.packageName))
        }
    }

    val glowColor by produceState(initialValue = PlayColors.Tile, app.packageName) {
        value = withContext(Dispatchers.Default) { repository.accent(app.packageName) }
    }

    TileFrame(
        selected = selected,
        moving = moving,
        background = PlayColors.Tile,
        glowColor = glowColor,
    ) {
        when (val current = art) {
            is TileArt.Banner -> Image(
                bitmap = current.bitmap,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            is TileArt.Glyph -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                current.icon?.let { (bitmap, fullBleed) ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = if (fullBleed) ContentScale.Crop else ContentScale.Fit,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(9.dp)),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = app.label,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            null -> Unit
        }
    }
}

@Composable
private fun AllAppsTile(selected: Boolean) {
    TileFrame(
        selected = selected,
        moving = false,
        background = Color.White.copy(alpha = 0.10f),
        glowColor = Color.White.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "All apps",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// Continue watching row
@Composable
private fun ContinueWatchingRow(
    items: List<WatchNextItem>,
    repository: TvWatchNextRepository,
    selectedIndex: MutableIntState,
    cwRequester: FocusRequester,
    onLaunch: (WatchNextItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sounds = LocalTvSounds.current
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selectedIndex.intValue - 1).coerceAtLeast(0),
    )
    var rowFocused by remember { mutableStateOf(value = false) }

    LaunchedEffect(listState) {
        snapshotFlow { selectedIndex.intValue }.collectLatest { selected ->
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == selected }
            if (item == null) {
                listState.animateScrollToItem(selected)
            } else {
                val start = info.viewportStartOffset + info.beforeContentPadding
                val end = info.viewportEndOffset - info.afterContentPadding
                val itemEnd = item.offset + item.size
                when {
                    itemEnd > end -> listState.animateScrollBy((itemEnd - end).toFloat(), tween(200))
                    item.offset < start -> listState.animateScrollBy((item.offset - start).toFloat(), tween(200))
                }
            }
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(188.dp)
            .focusRequester(cwRequester)
            .onFocusChanged { rowFocused = it.isFocused }
            .onKeyEvent { event ->
                val selected = selectedIndex.intValue
                val down = event.type == KeyEventType.KeyDown
                val up = event.type == KeyEventType.KeyUp

                when (event.key) {
                    Key.DirectionLeft -> {
                        if (down && (selected > 0)) {
                            sounds.playNavLeft()
                            selectedIndex.intValue = selected - 1
                        }
                        true
                    }

                    Key.DirectionRight -> {
                        if (down && (selected < items.lastIndex)) {
                            sounds.playNavRight()
                            selectedIndex.intValue = selected + 1
                        }
                        true
                    }

                    Key.DirectionUp -> {
                        if (down) {
                            sounds.playNavUp()
                        }
                        false
                    }

                    Key.DirectionDown -> true

                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (up) {
                            sounds.playClick()
                            items.getOrNull(selected)?.let(onLaunch)
                        }
                        true
                    }

                    else -> false
                }
            }
            .focusable(),
        contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        userScrollEnabled = false,
    ) {
        itemsIndexed(items, key = { _, (id) -> id }) { index, item ->
            ContinueWatchingCard(
                item = item,
                repository = repository,
                selected = rowFocused && (selectedIndex.intValue == index),
            )
        }
    }
}

// TV program card with thumbnail poster, progress and playback details
@Composable
private fun ContinueWatchingCard(
    item: WatchNextItem,
    repository: TvWatchNextRepository,
    selected: Boolean,
) {
    val focus by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(140),
        label = "cwFocus",
    )

    val thumbnailBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        item.id,
        item.thumbnailUri,
        item.posterArtUri,
    ) {
        value = repository.loadImage(item)
    }

    val glowColor by produceState(
        initialValue = Color(0xFF26335F),
        item.id,
        thumbnailBitmap,
    ) {
        value = withContext(Dispatchers.Default) {
            repository.extractAccent(item, thumbnailBitmap)
        }
    }

    val gradientColors = remember(item.fallbackGradient) {
        item.fallbackGradient?.map { Color(it) }
            ?: listOf(Color(0xFF1B2333), Color(0xFF090D14))
    }

    val density = LocalDensity.current
    val baseBlur = remember(density) {
        BlurMaskFilter(density.run { 8.dp.toPx() }, BlurMaskFilter.Blur.NORMAL)
    }
    val glowBlur = remember(density) {
        BlurMaskFilter(density.run { 12.dp.toPx() }, BlurMaskFilter.Blur.NORMAL)
    }

    Box(
        modifier = Modifier
            .zIndex(if (selected) 1f else 0f)
            .size(width = 256.dp, height = 144.dp)
            .graphicsLayer {
                val scale = 1f + (0.06f * focus)
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                val cornerRadiusPx = 14.dp.toPx()
                val offsetY = 3.dp.toPx()

                drawIntoCanvas { canvas ->
                    cardShadowPaint.color = android.graphics.Color.argb(100, 0, 0, 0)
                    cardShadowPaint.maskFilter = baseBlur
                    canvas.nativeCanvas.drawRoundRect(
                        0f,
                        offsetY,
                        size.width,
                        size.height + offsetY,
                        cornerRadiusPx,
                        cornerRadiusPx,
                        cardShadowPaint,
                    )

                    if (focus > 0.01f) {
                        val spread = 2.dp.toPx()
                        val glowAlpha = (glowColor.alpha * 0.70f * focus * 255).toInt().coerceIn(0, 255)
                        cardShadowPaint.color = android.graphics.Color.argb(
                            glowAlpha,
                            (glowColor.red * 255).toInt(),
                            (glowColor.green * 255).toInt(),
                            (glowColor.blue * 255).toInt(),
                        )
                        cardShadowPaint.maskFilter = glowBlur
                        canvas.nativeCanvas.drawRoundRect(
                            -spread,
                            -spread + offsetY + 1.dp.toPx(),
                            size.width + spread,
                            size.height + spread + offsetY + 1.dp.toPx(),
                            cornerRadiusPx + spread,
                            cornerRadiusPx + spread,
                            cardShadowPaint,
                        )
                    }
                }
            }
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(gradientColors))
            .drawWithContent {
                drawContent()
                if (focus > 0.01f) {
                    drawRing(Color.White.copy(alpha = focus), 3.dp.toPx(), 14.dp.toPx())
                }
            },
    ) {
        if (thumbnailBitmap != null) {
            Image(
                bitmap = thumbnailBitmap!!,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Gradient overlay for contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color(0x66000000),
                        1f to Color(0xEE04060C),
                    ),
                ),
        )

        // Metadata and progress
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (item.subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (item.progress > 0f) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.24f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = item.progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(Color.White),
                    )
                }
            }
        }
    }
}


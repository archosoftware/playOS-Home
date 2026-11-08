package com.archosoftware.home.playos.ui

import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import com.archosoftware.home.playos.data.AppEntry
import com.archosoftware.home.playos.data.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    favorites: Set<String>,
    hiddenApps: Set<String>,
    repository: AppRepository,
    onLaunch: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    onOpenSettings: () -> Unit,
    onToggleFavorite: (AppEntry) -> Unit,
    onToggleHide: (AppEntry) -> Unit,
    onForceStop: (AppEntry) -> Unit,
) {
    var tab by remember { mutableIntStateOf(if (favorites.isNotEmpty()) TAB_APPS else TAB_MEDIA) }
    val focusedIndex = remember { mutableIntStateOf(1) }
    var searching by remember { mutableStateOf(value = false) }
    var showHiddenDialog by remember { mutableStateOf(value = false) }
    var isMoving by remember { mutableStateOf(value = false) }
    var moveSnapshot by remember { mutableStateOf<List<String>?>(null) }
    var orderVersion by remember { mutableIntStateOf(0) }
    var menuTarget by remember { mutableStateOf<AppEntry?>(null) }

    val rowRequester = remember { FocusRequester() }
    val searchRequester = remember { FocusRequester() }
    val tabRequesters = remember { List(2) { FocusRequester() } }

    var refocus by remember { mutableStateOf<Pair<Int, FocusRequester>?>(null) }
    fun requestRefocus(target: FocusRequester) {
        refocus = ((refocus?.first ?: 0) + 1) to target
    }

    val context = LocalContext.current
    val visibleApps = remember(apps, favorites, hiddenApps, tab, orderVersion, context.packageName) {
        val base = if (tab == TAB_APPS) {
            apps.filter { (_, packageName) -> packageName in favorites }
        } else {
            apps
        }
        val filtered = base.filter { (_, packageName) ->
            !packageName.equals(context.packageName, ignoreCase = true) && (packageName !in hiddenApps)
        }
        repository.applyOrder(filtered, repository.loadOrder(tab))
    }

    val searchableApps = remember(apps, hiddenApps, context.packageName) {
        apps.filter { (_, packageName) ->
            !packageName.equals(context.packageName, ignoreCase = true) && (packageName !in hiddenApps)
        }
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        if ((fromIndex !in visibleApps.indices) || (toIndex !in visibleApps.indices) || (fromIndex == toIndex)) return
        val list = visibleApps.toMutableList()
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)

        val newPkgOrder = list.map { (_, packageName) -> packageName }
        val existingSaved = repository.loadOrder(tab)
        val remaining = existingSaved.filter { it !in newPkgOrder.toSet() }
        repository.saveOrder(tab, newPkgOrder + remaining)

        focusedIndex.intValue = toIndex
        orderVersion++
    }

    fun sortAZ() {
        val sorted = visibleApps.asSequence().sortedBy { (label) -> label.lowercase() }.map { (_, packageName) -> packageName }.toList()
        val existingSaved = repository.loadOrder(tab)
        val remaining = existingSaved.filter { it !in sorted.toSet() }
        repository.saveOrder(tab, sorted + remaining)
        orderVersion++
    }

    fun sortZA() {
        val sorted = visibleApps.asSequence().sortedByDescending { (label) -> label.lowercase() }.map { (_, packageName) -> packageName }.toList()
        val existingSaved = repository.loadOrder(tab)
        val remaining = existingSaved.filter { it !in sorted.toSet() }
        repository.saveOrder(tab, sorted + remaining)
        orderVersion++
    }

    LaunchedEffect(refocus) {
        val target = refocus?.second ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { target.requestFocus() }
    }

    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty()) {
            withFrameNanos { }
            val target = if (visibleApps.isNotEmpty()) rowRequester else tabRequesters[tab]
            runCatching { target.requestFocus() }
        }
    }

    LaunchedEffect(tab, visibleApps.isEmpty()) {
        focusedIndex.intValue = if (visibleApps.size > 1) 1 else 0
    }

    LaunchedEffect(visibleApps.size) {
        focusedIndex.intValue =
            focusedIndex.intValue.coerceIn(0, (visibleApps.size - 1).coerceAtLeast(0))
    }

    val rowAlpha = remember(tab) { Animatable(0f) }
    LaunchedEffect(tab) { rowAlpha.animateTo(1f, tween(180)) }

    Box(modifier = Modifier.fillMaxSize()) {
        AccentBackground(visibleApps, focusedIndex, repository)

        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                selectedTab = tab,
                hasHiddenApps = hiddenApps.isNotEmpty(),
                tabRequesters = tabRequesters,
                searchRequester = searchRequester,
                onSelectTab = { index ->
                    if (index != tab) {
                        isMoving = false
                        moveSnapshot = null
                        val newBase = if (index == TAB_APPS) {
                            apps.filter { (_, packageName) -> packageName in favorites }
                        } else {
                            apps
                        }
                        val newCount = newBase.count { (_, packageName) ->
                            !packageName.equals(context.packageName, ignoreCase = true) && (packageName !in hiddenApps)
                        }
                        tab = index
                        focusedIndex.intValue = if (newCount > 1) 1 else 0
                    }
                },
                onSearch = { searching = true },
                onOpenHidden = { showHiddenDialog = true },
                onOpenSettings = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = RowAnchor - 16.dp,
                        end = ScreenPadding,
                        top = 20.dp,
                        bottom = 8.dp,
                    ),
            )

            key(tab) {
                AppRow(
                    apps = visibleApps,
                    repository = repository,
                    favorites = favorites,
                    showFavoriteBadge = tab == TAB_MEDIA,
                    focusedIndex = focusedIndex,
                    onLaunch = onLaunch,
                    onLongPress = { menuTarget = it },
                    rowRequester = rowRequester,
                    modifier = Modifier
                        .focusProperties { up = tabRequesters[tab] }
                        .graphicsLayer { alpha = rowAlpha.value },
                    isMoving = isMoving,
                    onMoveItem = ::moveItem,
                    onFinishMove = {
                        moveSnapshot = null
                        isMoving = false
                    },
                ) {
                    moveSnapshot?.let { repository.saveOrder(tab, it) }
                    moveSnapshot = null
                    isMoving = false
                    orderVersion++
                }
            }

            Spacer(Modifier.weight(1f))

            val bottomPadding = Modifier.padding(start = RowAnchor, end = ScreenPadding, bottom = 48.dp)
            when {
                apps.isEmpty() -> Unit
                visibleApps.isEmpty() -> EmptyState(bottomPadding)
                else -> Hero(
                    apps = visibleApps,
                    focusedIndex = focusedIndex,
                    onLaunch = onLaunch,
                    onMore = { menuTarget = it },
                    modifier = bottomPadding,
                )
            }
        }

        menuTarget?.let { app ->
            val wasFavorite = app.packageName in favorites
            ActionMenu(
                app = app,
                isFavorite = wasFavorite,
                onDismiss = {
                    menuTarget = null
                    if (!searching) requestRefocus(rowRequester)
                },
                onMove = {
                    menuTarget = null
                    moveSnapshot = repository.loadOrder(tab)
                    isMoving = true
                    requestRefocus(rowRequester)
                },
                onSortAZ = {
                    menuTarget = null
                    sortAZ()
                    requestRefocus(rowRequester)
                },
                onSortZA = {
                    menuTarget = null
                    sortZA()
                    requestRefocus(rowRequester)
                },
                onHide = {
                    menuTarget = null
                    onToggleHide(app)
                    requestRefocus(rowRequester)
                },
                onToggleFavorite = {
                    onToggleFavorite(app)
                    menuTarget = null
                    when {
                        searching -> Unit
                        wasFavorite && (tab == TAB_APPS) && (visibleApps.size <= 1) ->
                            requestRefocus(tabRequesters[TAB_APPS])
                        else -> requestRefocus(rowRequester)
                    }
                },
                onAppInfo = {
                    menuTarget = null
                    onAppInfo(app)
                },
                onLaunch = {
                    menuTarget = null
                    searching = false
                    onLaunch(app)
                },
            ) {
                onForceStop(app)
                menuTarget = null
                if (!searching) requestRefocus(rowRequester)
            }
        }

        if (showHiddenDialog) {
            val hiddenList = remember(apps, hiddenApps) {
                apps.filter { (_, packageName) -> packageName in hiddenApps }
            }
            HiddenAppsDialog(
                hiddenApps = hiddenList,
                onUnhide = onToggleHide,
            ) {
                showHiddenDialog = false
                requestRefocus(rowRequester)
            }
        }

        if (searching) {
            SearchOverlay(
                apps = searchableApps,
                favorites = favorites,
                repository = repository,
                onLaunch = {
                    searching = false
                    onLaunch(it)
                },
                onLongPress = { menuTarget = it },
            ) {
                searching = false
                requestRefocus(searchRequester)
            }
        }
    }
}

@Composable
private fun AccentBackground(
    apps: List<AppEntry>,
    focusedIndex: MutableIntState,
    repository: AppRepository,
) {
    val pkg = apps.getOrNull(focusedIndex.intValue)?.packageName
    val target by produceState(AppRepository.DefaultAccent, pkg) {
        value = if (pkg == null) AppRepository.DefaultAccent
        else withContext(Dispatchers.Default) { repository.accent(pkg) }
    }
    val accent = animateColorAsState(target, tween(450), label = "accent")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val a = accent.value
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            lerp(PlayColors.Base, a, 0.55f),
                            lerp(PlayColors.Base, a, 0.22f),
                            PlayColors.Base,
                        ),
                    ),
                )
            },
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TopBar(
    selectedTab: Int,
    hasHiddenApps: Boolean,
    tabRequesters: List<FocusRequester>,
    searchRequester: FocusRequester,
    onSelectTab: (Int) -> Unit,
    onSearch: () -> Unit,
    onOpenHidden: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        TabLabel(
            text = "Apps",
            selected = selectedTab == TAB_APPS,
            onSelect = { onSelectTab(TAB_APPS) },
            modifier = Modifier.focusRequester(tabRequesters[TAB_APPS]),
        )
        Spacer(Modifier.width(8.dp))
        TabLabel(
            text = "Media",
            selected = selectedTab == TAB_MEDIA,
            onSelect = { onSelectTab(TAB_MEDIA) },
            modifier = Modifier.focusRequester(tabRequesters[TAB_MEDIA]),
        )

        Spacer(Modifier.weight(1f))

        if (hasHiddenApps) {
            TopIconButton(onClick = onOpenHidden) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = "Hidden Apps",
                    modifier = Modifier.size(24.dp),
                    tint = Color.White,
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        TopIconButton(onClick = onSearch, modifier = Modifier.focusRequester(searchRequester)) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                modifier = Modifier.size(24.dp),
                tint = Color.White,
            )
        }
        Spacer(Modifier.width(8.dp))
        TopIconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                modifier = Modifier.size(24.dp),
                tint = Color.White,
            )
        }
        Spacer(Modifier.width(24.dp))
        Clock()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TabLabel(
    text: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onSelect,
        modifier = modifier.onFocusChanged { if (it.isFocused || it.hasFocus) onSelect() },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.14f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else PlayColors.TextDim,
            fontSize = 22.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TopIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.14f),
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, PlayColors.Ring), shape = CircleShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun Clock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay((60_000L - (now % 60_000L)).milliseconds)
        }
    }

    val text = remember(now) { DateFormat.getTimeFormat(context).format(Date(now)) }

    Text(
        text = text,
        color = Color.White.copy(alpha = 0.9f),
        fontSize = 24.sp,
        fontWeight = FontWeight.Light,
        modifier = modifier,
    )
}

@Composable
private fun Hero(
    apps: List<AppEntry>,
    focusedIndex: MutableIntState,
    onLaunch: (AppEntry) -> Unit,
    onMore: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = apps.getOrNull(focusedIndex.intValue) ?: return

    Column(modifier = modifier) {
        Crossfade(targetState = app, animationSpec = tween(180), label = "heroText") { (label, packageName) ->
            Column {
                Box(
                    modifier = Modifier
                        .border(1.5.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "App",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 600.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = packageName,
                    color = PlayColors.TextDim,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 600.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillButton { onLaunch(app) }
            MoreButton { onMore(app) }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "No favorites yet",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Open Media, hold OK on an app and choose Favorite.",
            color = PlayColors.TextDim,
            fontSize = 18.sp,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PillButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.14f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(
            text = "Launch",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 36.dp, vertical = 12.dp),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MoreButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.14f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "More options",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

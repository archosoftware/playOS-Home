package com.archosoftware.home.playos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import com.archosoftware.home.playos.data.AppEntry
import com.archosoftware.home.playos.data.AppRepository
import com.archosoftware.home.playos.data.TvWatchNextRepository

private enum class Page { Home, AllApps, Details }

private data class MenuRequest(val app: AppEntry)

@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    favorites: Set<String>,
    hiddenApps: Set<String>,
    repository: AppRepository,
    watchNextRepository: TvWatchNextRepository? = null,
    onLaunch: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    onOpenSettings: () -> Unit,
    onToggleFavorite: (AppEntry) -> Unit,
    onToggleHide: (AppEntry) -> Unit,
    onForceStop: (AppEntry) -> Unit,
) {
    val context = LocalContext.current
    val watchNext = watchNextRepository ?: remember(context) { TvWatchNextRepository(context) }
    val selfPackage = context.packageName

    var page by remember { mutableStateOf(Page.Home) }
    var detailsApp by remember { mutableStateOf<AppEntry?>(null) }
    var detailsReturn by remember { mutableStateOf(Page.Home) }

    var menu by remember { mutableStateOf<MenuRequest?>(null) }
    var searching by remember { mutableStateOf(value = false) }
    var showHidden by remember { mutableStateOf(value = false) }
    var showQuick by remember { mutableStateOf(value = false) }

    var isMoving by remember { mutableStateOf(value = false) }
    var moveSnapshot by remember { mutableStateOf<List<String>?>(null) }
    var orderVersion by remember { mutableIntStateOf(0) }

    // State-backed index for the home row; decoupled from root recomposition
    val homeIndex = remember { mutableIntStateOf(0) }

    val rowRequester = remember { FocusRequester() }
    val searchRequester = remember { FocusRequester() }
    val clockRequester = remember { FocusRequester() }
    val gridFirst = remember { FocusRequester() }
    val detailsOpen = remember { FocusRequester() }

    // Pair counter guarantees LaunchedEffect re-runs even when requesting the same target twice
    var refocus by remember { mutableStateOf<Pair<Int, FocusRequester>?>(null) }
    fun requestRefocus(target: FocusRequester) {
        refocus = ((refocus?.first ?: 0) + 1) to target
    }

    fun refocusCurrentPage() {
        requestRefocus(
            when (page) {
                Page.Home -> rowRequester
                Page.AllApps -> gridFirst
                Page.Details -> detailsOpen
            },
        )
    }

    val sounds = rememberTvSounds()

    fun goHome() {
        sounds.playClick()
        page = Page.Home
        requestRefocus(rowRequester)
    }

    fun openAllApps() {
        sounds.playClick()
        page = Page.AllApps
        requestRefocus(gridFirst)
    }

    fun openDetails(app: AppEntry) {
        sounds.playClick()
        if (page != Page.Details) detailsReturn = page
        detailsApp = app
        page = Page.Details
        requestRefocus(detailsOpen)
    }

    fun leaveDetails() {
        sounds.playClick()
        page = detailsReturn
        requestRefocus(if (detailsReturn == Page.AllApps) gridFirst else rowRequester)
    }

    // Filter out this launcher package and user-hidden apps
    val launchable = remember(apps, hiddenApps, selfPackage) {
        apps.filter { (_, packageName) ->
            !packageName.equals(selfPackage, ignoreCase = true) && (packageName !in hiddenApps)
        }
    }

    val pinned = remember(launchable, favorites, orderVersion) {
        repository.applyOrder(
            launchable.filter { (_, packageName) -> packageName in favorites },
            repository.loadOrder(TAB_APPS),
        )
    }

    fun savePinnedOrder(sorted: List<String>) {
        val sortedSet = sorted.toSet()
        val remaining = repository.loadOrder(TAB_APPS).filter { it !in sortedSet }
        repository.saveOrder(TAB_APPS, sorted + remaining)
        orderVersion++
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        if ((fromIndex !in pinned.indices) || (toIndex !in pinned.indices) || (fromIndex == toIndex)) return
        val list = pinned.toMutableList()
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        savePinnedOrder(list.map(AppEntry::packageName))
        homeIndex.intValue = toIndex
    }

    fun sortAZ() {
        savePinnedOrder(
            pinned.asSequence().sortedBy { (label) -> label.lowercase() }.map(AppEntry::packageName).toList(),
        )
    }

    fun sortZA() {
        savePinnedOrder(
            pinned.asSequence().sortedByDescending { (label) -> label.lowercase() }.map(AppEntry::packageName).toList(),
        )
    }

    // Wait for layout pass before requesting TV focus
    LaunchedEffect(refocus) {
        val target = refocus?.second ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { target.requestFocus() }
    }

    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty()) {
            withFrameNanos { }
            runCatching { rowRequester.requestFocus() }
        }
    }

    // Clamp selection when pinned apps list changes
    LaunchedEffect(pinned.size) {
        homeIndex.intValue = homeIndex.intValue.coerceIn(0, pinned.size)
    }

    // Root page consumes Back; sub-pages return to home/caller
    BackHandler(enabled = page != Page.Home) {
        sounds.playClick()
        if (page == Page.Details) leaveDetails() else goHome()
    }

    CompositionLocalProvider(LocalTvSounds provides sounds) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PlayColors.Base),
        ) {
        Crossfade(targetState = page, animationSpec = tween(160), label = "page") { current ->
            when (current) {
                Page.Home -> HomeContent(
                    pinned = pinned,
                    repository = repository,
                    watchNextRepository = watchNext,
                    selectedIndex = homeIndex,
                    isMoving = isMoving,
                    hasHiddenApps = hiddenApps.isNotEmpty(),
                    rowRequester = rowRequester,
                    searchRequester = searchRequester,
                    clockRequester = clockRequester,
                    onLaunch = onLaunch,
                    onLongPress = { menu = MenuRequest(it) },
                    onOpenAllApps = { openAllApps() },
                    onMoveItem = { from, to -> moveItem(from, to) },
                    onFinishMove = {
                        moveSnapshot = null
                        isMoving = false
                    },
                    onCancelMove = {
                        moveSnapshot?.let { repository.saveOrder(TAB_APPS, it) }
                        moveSnapshot = null
                        isMoving = false
                        orderVersion++
                    },
                    onSearch = { searching = true },
                    onOpenHidden = { showHidden = true },
                    onOpenSettings = onOpenSettings,
                ) { showQuick = true }

                Page.AllApps -> AllAppsScreen(
                    apps = launchable,
                    favorites = favorites,
                    repository = repository,
                    firstItemRequester = gridFirst,
                    onBack = { goHome() },
                    onLaunch = onLaunch,
                ) { menu = MenuRequest(it) }

                Page.Details -> detailsApp?.let { app ->
                    AppDetailsScreen(
                        app = app,
                        repository = repository,
                        isPinned = app.packageName in favorites,
                        openRequester = detailsOpen,
                        onBack = { leaveDetails() },
                        onOpen = { onLaunch(app) },
                        onTogglePin = { onToggleFavorite(app) },
                    ) { onAppInfo(app) }
                }
            }
        }

        menu?.let { (app) ->
            ActionMenu(
                app = app,
                repository = repository,
                isPinned = app.packageName in favorites,
                showReorder = (page == Page.Home) && !searching && (app in pinned),
                onDismiss = {
                    menu = null
                    if (!searching) refocusCurrentPage()
                },
                onOpen = {
                    menu = null
                    searching = false
                    onLaunch(app)
                },
                onTogglePin = {
                    onToggleFavorite(app)
                    menu = null
                    if (!searching) refocusCurrentPage()
                },
                onMove = {
                    menu = null
                    moveSnapshot = repository.loadOrder(TAB_APPS)
                    isMoving = true
                    homeIndex.intValue = pinned.indexOf(app).coerceAtLeast(0)
                    requestRefocus(rowRequester)
                },
                onSortAZ = {
                    menu = null
                    sortAZ()
                    requestRefocus(rowRequester)
                },
                onSortZA = {
                    menu = null
                    sortZA()
                    requestRefocus(rowRequester)
                },
                onAppInfo = {
                    menu = null
                    searching = false
                    openDetails(app)
                },
                onHide = {
                    menu = null
                    onToggleHide(app)
                    if (!searching) refocusCurrentPage()
                },
            ) {
                onForceStop(app)
                menu = null
                if (!searching) refocusCurrentPage()
            }
        }

        if (showHidden) {
            val hiddenList = remember(apps, hiddenApps) {
                apps.filter { (_, packageName) -> packageName in hiddenApps }
            }
            HiddenAppsDialog(
                hiddenApps = hiddenList,
                onUnhide = onToggleHide,
            ) {
                sounds.playClick()
                showHidden = false
                refocusCurrentPage()
            }
        }

        if (showQuick) {
            QuickSettingsDialog(
                onOpenSettings = onOpenSettings,
            ) {
                sounds.playClick()
                showQuick = false
                requestRefocus(clockRequester)
            }
        }

        if (searching) {
            SearchOverlay(
                apps = launchable,
                favorites = favorites,
                repository = repository,
                onLaunch = {
                    searching = false
                    onLaunch(it)
                },
                onLongPress = { menu = MenuRequest(it) },
            ) {
                sounds.playClick()
                searching = false
                requestRefocus(searchRequester)
            }
        }
    }
}
}

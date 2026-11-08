package com.archosoftware.playos.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.archosoftware.playos.data.AppEntry
import com.archosoftware.playos.data.AppRepository
import kotlinx.coroutines.launch

@Composable
internal fun ActionMenu(
    app: AppEntry,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onMove: () -> Unit,
    onSortAZ: () -> Unit,
    onSortZA: () -> Unit,
    onHide: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAppInfo: () -> Unit,
    onLaunch: () -> Unit,
    onForceStop: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val appear = remember { Animatable(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LaunchedEffect(Unit) {
            launch { appear.animateTo(1f, tween(160)) }
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }

        Column(
            modifier = Modifier
                .width(320.dp)
                .graphicsLayer {
                    alpha = appear.value
                    val s = 0.94f + (0.06f * appear.value)
                    scaleX = s
                    scaleY = s
                }
                .clip(RoundedCornerShape(20.dp))
                .background(PlayColors.Card)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = app.packageName,
                color = PlayColors.TextDim,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))

            MenuItem(
                text = "Move",
                onClick = onMove,
                modifier = Modifier.focusRequester(first),
            )
            MenuItem(
                text = if (isFavorite) "Unfavorite" else "Favorite",
                onClick = onToggleFavorite,
            )
            MenuItem(text = "Sort A-Z", onClick = onSortAZ)
            MenuItem(text = "Sort Z-A", onClick = onSortZA)
            MenuItem(text = "Hide App", onClick = onHide)
            MenuItem(text = "App Info", onClick = onAppInfo)
            MenuItem(text = "Launch", onClick = onLaunch)
            MenuItem(text = "Force Stop", onClick = onForceStop)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun HiddenAppsDialog(
    hiddenApps: List<AppEntry>,
    onUnhide: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val appear = remember { Animatable(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LaunchedEffect(Unit) {
            launch { appear.animateTo(1f, tween(160)) }
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }

        Column(
            modifier = Modifier
                .width(380.dp)
                .graphicsLayer {
                    alpha = appear.value
                    val s = 0.94f + (0.06f * appear.value)
                    scaleX = s
                    scaleY = s
                }
                .clip(RoundedCornerShape(20.dp))
                .background(PlayColors.Card)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(
                text = "Hidden Apps",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))

            if (hiddenApps.isEmpty()) {
                Text(
                    text = "No hidden apps",
                    color = PlayColors.TextDim,
                    fontSize = 16.sp,
                )
            } else {
                hiddenApps.forEachIndexed { index, app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                        ) {
                            Text(
                                text = app.label,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = app.packageName,
                                color = PlayColors.TextDim,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Surface(
                            onClick = { onUnhide(app) },
                            modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black,
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        ) {
                            Text(
                                text = "Unhide",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MenuItem(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}

@Composable
internal fun SearchOverlay(
    apps: List<AppEntry>,
    favorites: Set<String>,
    repository: AppRepository,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { (label) -> label.contains(q, ignoreCase = true) }
    }
    val focusedIndex = remember { mutableIntStateOf(if (apps.size > 1) 1 else 0) }
    val fieldRequester = remember { FocusRequester() }
    val rowRequester = remember { FocusRequester() }
    val appear = remember { Animatable(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current

        LaunchedEffect(Unit) {
            launch { appear.animateTo(1f, tween(160)) }
            withFrameNanos { }
            runCatching {
                fieldRequester.requestFocus()
                keyboard?.show()
            }
        }
        LaunchedEffect(query) { focusedIndex.intValue = if (results.size > 1) 1 else 0 }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = appear.value }
                .background(PlayColors.Base),
        ) {
            Column(
                modifier = Modifier.padding(start = RowAnchor, end = ScreenPadding, top = 48.dp),
            ) {
                Text(
                    text = "Search",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(24.dp))

                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Light,
                    ),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboard?.hide()
                            focusManager.moveFocus(FocusDirection.Down)
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .focusRequester(fieldRequester),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawBehind {
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.6f),
                                        start = Offset(0f, size.height),
                                        end = Offset(size.width, size.height),
                                        strokeWidth = 2.dp.toPx(),
                                    )
                                }
                                .padding(bottom = 10.dp),
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Type to search apps",
                                    color = PlayColors.TextDim,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Light,
                                )
                            }
                            inner()
                        }
                    },
                )
                Spacer(Modifier.height(40.dp))
            }

            if (results.isEmpty()) {
                Text(
                    text = "No apps found",
                    color = PlayColors.TextDim,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(start = RowAnchor),
                )
            } else {
                AppRow(
                    apps = results,
                    repository = repository,
                    favorites = favorites,
                    showFavoriteBadge = true,
                    focusedIndex = focusedIndex,
                    onLaunch = onLaunch,
                    onLongPress = onLongPress,
                    rowRequester = rowRequester,
                )
            }
        }
    }
}

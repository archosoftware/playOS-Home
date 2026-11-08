package com.archosoftware.home.playos.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.archosoftware.home.playos.data.AppEntry
import com.archosoftware.home.playos.data.AppIcon
import com.archosoftware.home.playos.data.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

private const val FadedAlpha = 0.45f
private val TileTop = 8.dp
private const val LONG_PRESS_DURATION = 600L

@Composable
internal fun AppRow(
    apps: List<AppEntry>,
    repository: AppRepository,
    favorites: Set<String>,
    showFavoriteBadge: Boolean,
    focusedIndex: MutableIntState,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    rowRequester: FocusRequester,
    modifier: Modifier = Modifier,
    isMoving: Boolean = false,
    onMoveItem: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
    onFinishMove: (() -> Unit)? = null,
    onCancelMove: (() -> Unit)? = null,
) {
    val selected = focusedIndex.intValue
    val lastIndex = apps.lastIndex

    val position = remember {
        Animatable(focusedIndex.intValue.toFloat())
    }

    LaunchedEffect(Unit) {
        snapshotFlow { focusedIndex.intValue }
            .collectLatest { target ->
                position.animateTo(
                    target.toFloat(),
                    tween(
                        durationMillis = 220,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
    }

    val rowFocused = remember {
        mutableStateOf(value = false)
    }

    val scope = rememberCoroutineScope()
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var longPressFired by remember { mutableStateOf(value = false) }

    DisposableEffect(Unit) {
        onDispose {
            longPressJob?.cancel()
        }
    }

    val first = (selected - 2).coerceAtLeast(0)
    val last = (selected + 16).coerceAtMost(lastIndex)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(RowHeight)
            .focusRequester(rowRequester)
            .onFocusChanged {
                rowFocused.value = it.isFocused

                if (!it.isFocused) {
                    longPressJob?.cancel()
                    longPressJob = null
                    longPressFired = false
                }
            }
            .onKeyEvent { event ->
                if (isMoving) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if ((event.type == KeyEventType.KeyDown) && (focusedIndex.intValue > 0)) {
                                val cur = focusedIndex.intValue
                                onMoveItem?.invoke(cur, cur - 1)
                                focusedIndex.intValue = cur - 1
                            }
                            true
                        }

                        Key.DirectionRight -> {
                            if ((event.type == KeyEventType.KeyDown) && (focusedIndex.intValue < lastIndex)) {
                                val cur = focusedIndex.intValue
                                onMoveItem?.invoke(cur, cur + 1)
                                focusedIndex.intValue = cur + 1
                            }
                            true
                        }

                        Key.DirectionCenter,
                        Key.Enter,
                        Key.NumPadEnter,
                        -> {
                            if (event.type == KeyEventType.KeyUp) {
                                onFinishMove?.invoke()
                            }
                            true
                        }

                        Key.Back,
                        Key.Escape,
                        -> {
                            if (event.type == KeyEventType.KeyUp) {
                                onCancelMove?.invoke()
                            }
                            true
                        }

                        else -> false
                    }
                } else {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if ((event.type == KeyEventType.KeyDown) && apps.isNotEmpty()) {
                                focusedIndex.intValue = (focusedIndex.intValue - 1).coerceAtLeast(0)
                            }
                            true
                        }

                        Key.DirectionRight -> {
                            if ((event.type == KeyEventType.KeyDown) && apps.isNotEmpty()) {
                                focusedIndex.intValue = (focusedIndex.intValue + 1).coerceAtMost(lastIndex)
                            }
                            true
                        }

                        Key.DirectionCenter,
                        Key.Enter,
                        Key.NumPadEnter,
                        -> {
                            when (event.type) {
                                KeyEventType.KeyDown -> {
                                    if (longPressJob == null) {
                                        longPressFired = false
                                        val app = apps.getOrNull(focusedIndex.intValue)

                                        longPressJob = scope.launch {
                                            delay(LONG_PRESS_DURATION.milliseconds)
                                            if (app != null) {
                                                longPressFired = true
                                                onLongPress(app)
                                            }
                                        }
                                    }
                                }

                                KeyEventType.KeyUp -> {
                                    longPressJob?.cancel()
                                    longPressJob = null

                                    if (!longPressFired) {
                                        apps.getOrNull(focusedIndex.intValue)?.let(onLaunch)
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
            }
            .focusable(),
    ) {
        if (first <= last) {
            for (i in first..last) {
                val app = apps[i]
                key(app.packageName) {
                    CarouselTile(
                        index = i,
                        entry = app,
                        repository = repository,
                        position = position,
                        rowFocused = rowFocused,
                        isFavorite = showFavoriteBadge && (app.packageName in favorites),
                        isMoving = isMoving && (i == selected),
                    )
                }
            }
        }

        apps.getOrNull(selected)?.let { (label) ->
            Crossfade(
                targetState = label,
                animationSpec = tween(120),
                label = "tileLabel",
                modifier = Modifier.offset(
                    x = RowAnchor + FocusedTile + 14.dp,
                    y = TileTop + SmallTile + 10.dp,
                ),
            ) { activeLabel ->
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isMoving) PlayColors.Favorite else Color.White.copy(alpha = 0.9f))
                                .border(
                                    width = 1.5.dp,
                                    color = if (isMoving) PlayColors.Favorite else Color.White.copy(alpha = 0.9f),
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = if (isMoving) "Moving" else "App",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        Text(
                            text = activeLabel,
                            color = Color.White,
                            fontSize = 18.sp,
                            maxLines = 1,
                            modifier = Modifier.widthIn(max = 320.dp),
                        )
                    }

                    if (isMoving) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "◀ Left / Right to Move • OK to Place • Back to Cancel",
                            color = PlayColors.Favorite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CarouselTile(
    index: Int,
    entry: AppEntry,
    repository: AppRepository,
    position: Animatable<Float, AnimationVector1D>,
    rowFocused: State<Boolean>,
    isFavorite: Boolean,
    isMoving: Boolean = false,
) {
    val icon by produceState<AppIcon?>(
        initialValue = null,
        entry.packageName,
    ) {
        value = withContext(Dispatchers.Default) {
            repository.icon(entry.packageName)
        }
    }

    Box(
        modifier = Modifier
            .layout { measurable, _ ->
                val d = index - position.value
                val growth = (1f - abs(d)).coerceIn(0f, 1f)
                val side = (SmallTile + ((FocusedTile - SmallTile) * growth)).roundToPx()
                val extra = (FocusedTile - SmallTile) * d.coerceIn(0f, 1f)
                val x = (RowAnchor + ((SmallTile + TileGap) * d) + extra).roundToPx()

                val placeable = measurable.measure(Constraints.fixed(side, side))
                layout(side, side) {
                    placeable.place(x, TileTop.roundToPx())
                }
            }
            .graphicsLayer {
                val d = index - position.value
                alpha = when {
                    d >= 0f -> 1f
                    d >= -1f -> 1f + ((FadedAlpha - 1f) * (-d))
                    else -> (FadedAlpha * (2f + d)).coerceIn(0f, FadedAlpha)
                }
            }
            .drawWithContent {
                val active = (1f - abs(index - position.value)).coerceIn(0f, 1f)
                val radius = 12.dp.toPx()

                drawRoundRect(
                    color = lerp(PlayColors.Tile, PlayColors.TileFocused, active),
                    cornerRadius = CornerRadius(radius),
                )
                drawContent()

                if ((rowFocused.value && (active > 0.01f)) || isMoving) {
                    val stroke = (if (isMoving) 3.5.dp else 2.5.dp).toPx()
                    val ringColor = if (isMoving) PlayColors.Favorite else PlayColors.Ring.copy(alpha = PlayColors.Ring.alpha * active)
                    drawRoundRect(
                        color = ringColor,
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(radius - (stroke / 2f)),
                        style = Stroke(stroke),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        icon?.let { (bitmap, fullBleed) ->
            if (fullBleed) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(TileShape),
                )
            } else {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(0.62f),
                )
            }
        }

        if (isFavorite) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Favorite",
                tint = PlayColors.Favorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(16.dp),
            )
        }
    }
}

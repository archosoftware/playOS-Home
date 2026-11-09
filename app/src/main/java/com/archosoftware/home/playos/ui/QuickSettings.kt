@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.archosoftware.home.playos.ui

import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.archosoftware.home.playos.data.SystemStatus
import com.archosoftware.home.playos.data.openSystemScreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Quick settings flyout anchored below the clock in the top-right corner
@Composable
internal fun QuickSettingsDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val now = remember { Date() }
    val time = remember { DateFormat.getTimeFormat(context).format(now) }
    val date = remember { SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now) }
    val (network, bluetooth) = remember { SystemStatus.read(context) }

    val first = remember { FocusRequester() }
    val appear = remember { Animatable(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Pin dialog window to top-right corner
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setGravity(Gravity.TOP or Gravity.END)
        }

        LaunchedEffect(Unit) {
            launch { appear.animateTo(1f, tween(160)) }
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }

        Column(
            modifier = Modifier
                .padding(top = 76.dp, end = 56.dp)
                .width(320.dp)
                .graphicsLayer {
                    alpha = appear.value
                    val s = 0.95f + (0.05f * appear.value)
                    scaleX = s
                    scaleY = s
                }
                .clip(RoundedCornerShape(22.dp))
                .background(PlayColors.Card)
                .padding(20.dp),
        ) {
            Text(
                text = time,
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = date,
                color = PlayColors.TextDim,
                fontSize = 16.sp,
            )

            Spacer(Modifier.height(18.dp))

            val sounds = LocalTvSounds.current

            QuickRow(
                icon = Icons.Default.Wifi,
                title = "Network",
                subtitle = network,
                onClick = {
                    context.openSystemScreen(Settings.ACTION_WIFI_SETTINGS)
                    onDismiss()
                },
                modifier = Modifier
                    .focusRequester(first)
                    .quickRowNavSounds(sounds, canGoUp = false, canGoDown = true),
            )
            QuickRow(
                icon = Icons.Default.Bluetooth,
                title = "Bluetooth",
                subtitle = bluetooth,
                onClick = {
                    context.openSystemScreen(Settings.ACTION_BLUETOOTH_SETTINGS)
                    onDismiss()
                },
                modifier = Modifier.quickRowNavSounds(sounds, canGoUp = true, canGoDown = true),
            )
            QuickRow(
                icon = Icons.Default.Settings,
                title = "System settings",
                subtitle = "Open Android TV settings",
                onClick = {
                    onDismiss()
                    onOpenSettings()
                },
                modifier = Modifier.quickRowNavSounds(sounds, canGoUp = true, canGoDown = false),
            )
        }
    }
}

private fun Modifier.quickRowNavSounds(
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
private fun QuickRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
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
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = PlayColors.Accent,
            focusedContentColor = PlayColors.OnAccent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(text = subtitle, fontSize = 13.sp)
            }
        }
    }
}

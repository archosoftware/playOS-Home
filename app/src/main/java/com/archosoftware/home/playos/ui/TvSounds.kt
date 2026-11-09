package com.archosoftware.home.playos.ui

import android.os.SystemClock
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/**
 * aTV sounds controller
 * Dispatches built-in system sound effects
 */
class TvSounds(private val view: View) {
    private var lastNavSoundTime = 0L

    fun playClick() {
        runCatching {
            view.playSoundEffect(SoundEffectConstants.CLICK)
        }
    }

    fun playNavLeft() = playNav(SoundEffectConstants.NAVIGATION_LEFT)
    fun playNavRight() = playNav(SoundEffectConstants.NAVIGATION_RIGHT)
    fun playNavUp() = playNav(SoundEffectConstants.NAVIGATION_UP)
    fun playNavDown() = playNav(SoundEffectConstants.NAVIGATION_DOWN)

    private fun playNav(effect: Int) {
        val now = SystemClock.uptimeMillis()
        // Throttle rapid D-pad repeat events (50ms window) to avoid audio stutter
        if ((now - lastNavSoundTime) >= 50L) {
            lastNavSoundTime = now
            runCatching {
                view.playSoundEffect(effect)
            }
        }
    }
}

val LocalTvSounds = staticCompositionLocalOf<TvSounds> {
    error("LocalTvSounds was not provided")
}

@Composable
fun rememberTvSounds(): TvSounds {
    val view = LocalView.current
    return remember(view) { TvSounds(view) }
}

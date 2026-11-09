package com.archosoftware.home.playos

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.archosoftware.home.playos.data.AppEntry
import com.archosoftware.home.playos.data.AppRepository
import com.archosoftware.home.playos.ui.HomeScreen
import com.archosoftware.home.playos.ui.theme.PlayOSTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {

    private lateinit var repository: AppRepository

    private var apps by mutableStateOf<List<AppEntry>>(emptyList())
    private var favorites by mutableStateOf<Set<String>>(emptySet())
    private var hiddenApps by mutableStateOf<Set<String>>(emptySet())
    private var appsLoaded by mutableStateOf(value = false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.isSoundEffectsEnabled = true

        repository = AppRepository(applicationContext)
        favorites = repository.loadFavorites()
        hiddenApps = repository.loadHiddenApps()

        onBackPressedDispatcher.addCallback(
            owner = this,
            enabled = true,
        ) { }

        setContent {
            PlayOSTheme {
                var minimumSplashTimeElapsed by remember {
                    mutableStateOf(value = false)
                }

                LaunchedEffect(Unit) {
                    delay(1200.milliseconds)
                    minimumSplashTimeElapsed = true
                }

                val splashVisible = (!minimumSplashTimeElapsed) || (!appsLoaded)

                Box(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        apps = apps,
                        favorites = favorites,
                        hiddenApps = hiddenApps,
                        repository = repository,
                        onLaunch = ::launchApp,
                        onAppInfo = ::openAppInfo,
                        onOpenSettings = ::openSettings,
                        onToggleFavorite = ::toggleFavorite,
                        onToggleHide = ::toggleHide,
                        onForceStop = ::forceStop,
                    )

                    AnimatedVisibility(
                        visible = splashVisible,
                        modifier = Modifier.fillMaxSize(),
                        enter = fadeIn(
                            animationSpec = tween(250),
                        ),
                        exit = fadeOut(
                            animationSpec = tween(350),
                        ),
                    ) {
                        SplashScreen()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        lifecycleScope.launch {
            apps = withContext(Dispatchers.Default) {
                repository.loadApps()
            }
            appsLoaded = true
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        if (level >= TRIM_MEMORY_BACKGROUND) {
            repository.clearIconCache()
        }
    }

    private fun launchApp(entry: AppEntry) {
        val intent = repository.launchIntent(entry.packageName) ?: return

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
        )

        safeStart(intent)
    }

    private fun openAppInfo(entry: AppEntry) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", entry.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        safeStart(intent)
    }

    private fun openSettings() {
        safeStart(
            Intent(Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun toggleFavorite(entry: AppEntry) {
        favorites = repository.setFavorite(
            entry.packageName,
            entry.packageName !in favorites,
        )
    }

    private fun toggleHide(entry: AppEntry) {
        val willHide = entry.packageName !in hiddenApps
        hiddenApps = repository.setHidden(
            entry.packageName,
            willHide,
        )

        Toast.makeText(
            this,
            if (willHide) "Hidden ${entry.label}" else "Unhid ${entry.label}",
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun forceStop(entry: AppEntry) {
        repository.killBackgroundProcesses(entry.packageName)

        Toast.makeText(
            this,
            "Cleared ${entry.label} from memory",
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // ignore
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.playos_logo),
            contentDescription = "playOS",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(
                width = 276.dp,
                height = 80.dp,
            ),
        )
    }
}

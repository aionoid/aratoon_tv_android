package com.aionoid.aratoons

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aionoid.aratoons.ui.AraToonsNavHost
import com.aionoid.aratoons.ui.theme.AraToonsTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    companion object {
        const val PREFS = "aratoons"
        const val KEY_LANGUAGE = "language" // "" = follow system, "en", "ar"
    }

    /**
     * Backstop key handler for the player screen. Runs BEFORE the view
     * hierarchy receives the event, because the Compose focus owner can
     * swallow D-pad keys when no composable holds focus (e.g. right after
     * the controls overlay auto-hides), which no view-level OnKeyListener
     * can see. The player installs a handler while playback is active and
     * no dialog is open; a handler returning `false` lets the event fall
     * through to the normal dispatch (dialogs, back navigation, system keys).
     */
    private var playerKeyHandler: ((KeyEvent) -> Boolean)? = null

    fun setPlayerKeyHandler(handler: ((KeyEvent) -> Boolean)?) {
        playerKeyHandler = handler
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handler = playerKeyHandler
        if (handler != null && event.action == KeyEvent.ACTION_DOWN && handler(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Apply the user-selected interface language (EN/AR) to all resources. */
    override fun attachBaseContext(newBase: Context) {
        val lang = newBase.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "") ?: ""
        super.attachBaseContext(
            if (lang.isEmpty()) newBase else newBase.withLocale(Locale(lang))
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: content draws behind the (transparent) system bars
        enableEdgeToEdge()
        setContent {
            AraToonsTheme {
                AraToonsNavHost()
            }
        }
        hideSystemBars()
    }

    /**
     * Immersive mode (modern API): hide the status and navigation bars.
     * With BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE the bars stay hidden until
     * the user swipes in from a screen edge, then fade away on their own —
     * no deprecated systemUiVisibility flags.
     */
    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    // Re-hide after focus changes (e.g. returning from another app).
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }
}

/** Returns a copy of this context with the given locale applied to its resources. */
private fun Context.withLocale(locale: Locale): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
}

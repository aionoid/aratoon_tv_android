package com.aionoid.aratoons

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import com.aionoid.aratoons.ui.AraToonsNavHost
import com.aionoid.aratoons.ui.theme.AraToonsTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    companion object {
        const val PREFS = "aratoons"
        const val KEY_LANGUAGE = "language" // "" = follow system, "en", "ar"
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
        setContent {
            AraToonsTheme {
                AraToonsNavHost()
            }
        }
    }
}

/** Returns a copy of this context with the given locale applied to its resources. */
private fun Context.withLocale(locale: Locale): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
}

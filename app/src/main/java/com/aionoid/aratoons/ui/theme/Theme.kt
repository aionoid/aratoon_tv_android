package com.aionoid.aratoons.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme

/**
 * Brand colors (gold/amber accent on a dark background — streaming-app look).
 */
val Primary = Color(0xFFFFC107)
val OnPrimary = Color(0xFF212121)
val PrimaryContainer = Color(0xFF3A2E00)
val OnPrimaryContainer = Color(0xFFFFD75E)
val Secondary = Color(0xFFFFD75E)
val OnSecondary = Color(0xFF3A2E00)
val SecondaryContainer = Color(0xFF5A4400)
val OnSecondaryContainer = Color(0xFFFFD75E)
val Tertiary = Color(0xFFFFD75E)
val OnTertiary = Color(0xFF3A2E00)
val TertiaryContainer = Color(0xFF5A4400)
val OnTertiaryContainer = Color(0xFFFFD75E)
val Background = Color(0xFF0F1419)
val OnBackground = Color(0xFFE2E2E2)
val Surface = Color(0xFF1A1C1E)
val OnSurface = Color(0xFFE2E2E2)
val SurfaceVariant = Color(0xFF272B30)
val OnSurfaceVariant = Color(0xFFC4C7D0)
val Error = Color(0xFFFFB4AB)
val OnError = Color(0xFF690005)

/**
 * Material 3 (TV) theme — dark, high-contrast, 10-foot optimized.
 *
 * Wraps both the TV Material theme (androidx.tv:tv-material, M3 Expressive
 * focus glow/scale for TV components) and the standard Compose Material 3
 * theme (for plain M3 components).
 */
@Composable
fun AraToonsTheme(content: @Composable () -> Unit) {
    TvMaterialTheme(
        colorScheme = tvDarkColorScheme(
            primary = Primary,
            onPrimary = OnPrimary,
            primaryContainer = PrimaryContainer,
            onPrimaryContainer = OnPrimaryContainer,
            secondary = Secondary,
            onSecondary = OnSecondary,
            secondaryContainer = SecondaryContainer,
            onSecondaryContainer = OnSecondaryContainer,
            tertiary = Tertiary,
            onTertiary = OnTertiary,
            tertiaryContainer = TertiaryContainer,
            onTertiaryContainer = OnTertiaryContainer,
            background = Background,
            onBackground = OnBackground,
            surface = Surface,
            onSurface = OnSurface,
            surfaceVariant = SurfaceVariant,
            onSurfaceVariant = OnSurfaceVariant,
            error = Error,
            onError = OnError
        )
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Primary,
                onPrimary = OnPrimary,
                primaryContainer = PrimaryContainer,
                onPrimaryContainer = OnPrimaryContainer,
                secondary = Secondary,
                onSecondary = OnSecondary,
                secondaryContainer = SecondaryContainer,
                onSecondaryContainer = OnSecondaryContainer,
                tertiary = Tertiary,
                onTertiary = OnTertiary,
                tertiaryContainer = TertiaryContainer,
                onTertiaryContainer = OnTertiaryContainer,
                background = Background,
                onBackground = OnBackground,
                surface = Surface,
                onSurface = OnSurface,
                surfaceVariant = SurfaceVariant,
                onSurfaceVariant = OnSurfaceVariant,
                error = Error,
                onError = OnError
            )
        ) {
            // material3's MaterialTheme does NOT provide LocalContentColor (it defaults to
            // Color.Black). Provide a global light content color so bare Text/Icons outside
            // M3 components stay readable on the dark background. Components (Surface,
            // Button, ...) provide their own content color and override this.
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onBackground
            ) {
                content()
            }
        }
    }
}

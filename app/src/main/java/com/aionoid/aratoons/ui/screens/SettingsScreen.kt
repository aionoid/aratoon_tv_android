package com.aionoid.aratoons.ui.screens

import android.content.Context
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import com.aionoid.aratoons.BuildConfig
import com.aionoid.aratoons.MainActivity
import com.aionoid.aratoons.R
import com.aionoid.aratoons.ui.systemBarsContentPadding

data class LanguageOption(val label: String, val value: String)

/**
 * Settings: interface language (system / EN / AR) + about section.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val current = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        .getString(MainActivity.KEY_LANGUAGE, "") ?: ""

    var focusedRow by remember { mutableStateOf(false) }

    val options = listOf(
        LanguageOption(stringResource(R.string.language_system), ""),
        LanguageOption(stringResource(R.string.language_english), "en"),
        LanguageOption(stringResource(R.string.language_arabic), "ar")
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = systemBarsContentPadding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 32.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 8.dp)
            )
        }
        item {
            Text(
                text = stringResource(R.string.language),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
            )
        }
        items(options) { option ->
            SettingRow(
                label = option.label,
                selected = option.value == current,
                onClick = {
                    context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putString(MainActivity.KEY_LANGUAGE, option.value)
                        .apply()
                    // Recreate the activity to re-apply the locale
                    (context as? android.app.Activity)?.recreate()
                }
            )
        }
        item {
            Text(
                text = stringResource(R.string.about),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 4.dp)
            )
        }
        item {
            AboutCard()
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(vertical = 4.dp)
            .pointerInput(label) {
                // tv-material 1.0.1's tvClickable only handles D-pad enter — add touch
                detectTapGestures(onTap = { onClick() })
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .size(24.dp)
                        .align(Alignment.CenterVertically)
                )
            }
        }
    }
}

@Composable
private fun AboutCard() {
    Card(
        onClick = {},
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            Text(
                text = stringResource(R.string.about_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "${stringResource(R.string.version)} ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

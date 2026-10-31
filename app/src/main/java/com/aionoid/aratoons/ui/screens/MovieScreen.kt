package com.aionoid.aratoons.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.foundation.clickable
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.aionoid.aratoons.R
import com.aionoid.aratoons.data.Favorites
import com.aionoid.aratoons.data.StardimaApi
import com.aionoid.aratoons.ui.Routes
import com.aionoid.aratoons.ui.systemBarsContentPadding
import kotlinx.coroutines.delay

/**
 * Movie detail page — the single-video counterpart of [SeriesScreen].
 *
 * Series have a two-step seasons → episodes flow; a movie has none. So the
 * "info page" shows the poster + title + description + metadata on one screen
 * with a single primary action, **Watch now**, which hands off to
 * [ServerSelectScreen] (the same server-choose flow the series' play button
 * uses).
 *
 * Layout mirrors [SeriesScreen]: a fixed left column (poster + type badge) and
 * a wider right column (title, description, metadata, Watch-now button).
 * Focus lands on the Watch-now button on first compose; D‑pad CENTER activates.
 */
@Composable
fun MovieScreen(
    playUrl: String,
    title: String,
    poster: String,
    year: String,
    description: String,
    quality: String,
    navController: NavHostController
) {
    val gold = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val watchFocus = remember { FocusRequester() }
    val favEntry = Favorites.Entry(
        type = "movie",
        title = title,
        poster = poster,
        url = playUrl,
        year = year,
        desc = description,
        quality = quality
    )
    var isFavorite by remember(context, favEntry) {
        mutableStateOf(Favorites.contains(context, favEntry))
    }

    val barInsets = systemBarsContentPadding()

    // Focus the primary action on first compose (the only focusable element).
    LaunchedEffect(Unit) {
        delay(30L)
        try { watchFocus.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .padding(barInsets)
            .padding(24.dp)
            .fillMaxSize()
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Left: poster + type badge ──
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(230.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF2A2D31))
                ) {
                    if (poster.isNotEmpty()) {
                        AsyncImage(
                            model = StardimaApi.upscalePoster(poster, 780),
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            RoundedCornerShape(50)
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            RoundedCornerShape(50)
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Movie",
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 0.8.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ── Right: title + description + metadata + Watch now ──
            Column(
                modifier = Modifier.weight(2f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title.ifEmpty { "Untitled" },
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))

                val meta = listOf(year, quality).filter { it.isNotBlank() }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta.joinToString(" • "),
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 0.4.sp
                        ),
                        color = gold
                    )
                    Spacer(Modifier.height(10.dp))
                }

                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(32.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Watch now button
                    Button(
                        onClick = {
                            navController.navigate(Routes.servers(playUrl, title, poster))
                        },
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .focusRequester(watchFocus),
                        shape = ButtonDefaults.shape(RoundedCornerShape(14.dp)),
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            focusedContainerColor = MaterialTheme.colorScheme.primary,
                            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                            pressedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        )
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = stringResource(R.string.watch_now),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    // FAV button
                    val starVector = if (isFavorite) {
                        Icons.Filled.Star
                    } else {
                        Icons.Outlined.Star
                    }
                    val starColor = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    val favFocus = remember { FocusRequester() }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isFavorite) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                                RoundedCornerShape(50)
                            )
                            .border(
                                1.dp,
                                if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(50)
                            )
                            .padding(12.dp)
                            .focusRequester(favFocus)
                            .onKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when (event.key) {
                                    Key.Enter, Key.DirectionDown -> {
                                        isFavorite = Favorites.toggle(context, favEntry)
                                        true
                                    }
                                    else -> false
                                }
                            }
                            .clickable(interactionSource = null, indication = null) {
                                isFavorite = Favorites.toggle(context, favEntry)
                            },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = starVector,
                            contentDescription = stringResource(if (isFavorite) R.string.unfavorite else R.string.favorite),
                            tint = starColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

package com.aionoid.aratoons.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClassicCard
import androidx.tv.material3.WideClassicCard
import coil.compose.AsyncImage
import com.aionoid.aratoons.data.VideoItem

/**
 * A big 2:3 poster card for movies/series — the main browsing element.
 * Uses the TV Material classic card (M3 expressive focus scale + glow).
 */
@Composable
fun PosterCard(
    item: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    widthDp: Int = 160
) {
    ClassicCard(
        onClick = onClick,
        image = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(Color(0xFF2A2D31), RoundedCornerShape(10.dp))
            ) {
                if (item.posterUrl.isNotEmpty()) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(40.dp)
                    )
                }
                if (item.year.isNotEmpty()) {
                    Text(
                        text = item.year,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .background(
                                Color.Black.copy(alpha = 0.6f),
                                RoundedCornerShape(bottomStart = 10.dp, topEnd = 10.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        },
        modifier = modifier
            .width(widthDp.dp)
            .padding(6.dp)
            .pointerInput(Unit) {
                // tv-material 1.0.1's tvClickable only handles D-pad enter — add touch
                detectTapGestures(onTap = { onClick() })
            }
    )
}

/**
 * A wide 16:9 card for episodes / recently-watched rows.
 */
@Composable
fun WideCard(
    title: String,
    subtitle: String,
    posterUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    widthDp: Int = 300
) {
    WideClassicCard(
        onClick = onClick,
        image = {
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF2A2D31), RoundedCornerShape(10.dp))
            ) {
                if (posterUrl.isNotEmpty()) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        subtitle = {
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        modifier = modifier
            .width(widthDp.dp)
            .padding(6.dp)
            .pointerInput(Unit) {
                // tv-material 1.0.1's tvClickable only handles D-pad enter — add touch
                detectTapGestures(onTap = { onClick() })
            }
    )
}

/**
 * Big home-menu card (icon + label) — the primary navigation entry points.
 */
@Composable
fun MenuCard(
    label: String,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .width(190.dp)
            .height(110.dp)
            .padding(6.dp)
            .pointerInput(Unit) {
                // tv-material 1.0.1's tvClickable only handles D-pad enter — add touch
                detectTapGestures(onTap = { onClick() })
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        RoundedCornerShape(14.dp)
                    )
            ) {
                icon(MaterialTheme.colorScheme.primary)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/**
 * Section header for content rows.
 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
    )
}

/** Centered loading indicator. */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/** Error message with retry button. */
@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text("Retry")
        }
    }
}

/** Screen title bar. */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
    )
}

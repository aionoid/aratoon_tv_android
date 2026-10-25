package com.aionoid.aratoons.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import com.aionoid.aratoons.data.RecentlyWatched
import com.aionoid.aratoons.ui.Routes
import com.aionoid.aratoons.ui.systemBarsContentPadding

/**
 * Recently watched screen — a port of stardima_tv_flutter's
 * recently_watched_screen.dart: a header with the count badge and a
 * grid of 168x288 poster cards (poster + title + type).
 */
@Composable
fun RecentlyWatchedScreen(navController: NavHostController) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<RecentlyWatched.Entry>?>(null) }
    var loadKey by remember { mutableStateOf(0) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(loadKey) {
        entries = RecentlyWatched.load(context)
    }

    val list = entries ?: emptyList()

    // Leanback: start focus on the first card once data is in
    LaunchedEffect(entries) {
        if (list.isNotEmpty()) firstFocus.requestFocus()
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 168.dp),
        contentPadding = systemBarsContentPadding(
            start = 24.dp,
            top = 16.dp,
            end = 24.dp,
            bottom = 24.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.recently_watched),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                if (list.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            list.size.toString(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
        if (list.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_results),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 80.dp),
                    maxLines = 1
                )
            }
        }
        items(list, key = { it.url }) { entry ->
            RecentlyCard(
                entry = entry,
                onClick = {
                    when {
                        entry.url.contains("/tvshow/") ->
                            navController.navigate(Routes.series(entry.url.substringAfterLast("/")))
                        else ->
                            navController.navigate(Routes.servers(entry.url, entry.title, entry.poster))
                    }
                },
                focusRequester = if (entries == list) firstFocus else null
            )
        }
    }
}

/**
 * One recently-watched card: 168x288 — poster with a border (gold on
 * focus), the title and a movie/series type line beneath.
 */
@Composable
private fun RecentlyCard(
    entry: RecentlyWatched.Entry,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    val isSeries = entry.url.contains("/tvshow/") && !entry.url.contains("/play/")
    Column(
        modifier = Modifier
            .width(168.dp)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .focusable()
            .clickable(interactionSource = null, indication = null) { onClick() }
            .onFocusChanged { focused = it.hasFocus }
    ) {
        Box(
            modifier = Modifier
                .width(168.dp)
                .height(252.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2A2D31))
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.White.copy(alpha = 0.18f)
                    },
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            if (entry.poster.isNotEmpty()) {
                AsyncImage(
                    model = entry.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            ),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (isSeries) {
                stringResource(R.string.series)
            } else {
                stringResource(R.string.movies)
            },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = Color(0xFF9E9E9E)
        )
    }
}

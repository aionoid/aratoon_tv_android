package com.aionoid.aratoons.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.tv.material3.ListItem
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import coil.compose.AsyncImage
import com.aionoid.aratoons.App
import com.aionoid.aratoons.data.Episode
import com.aionoid.aratoons.data.SeasonInfo
import com.aionoid.aratoons.data.StardimaApi
import com.aionoid.aratoons.ui.ErrorBox
import com.aionoid.aratoons.ui.LoadingBox
import com.aionoid.aratoons.ui.Routes

/**
 * Series detail: poster + metadata, season tabs, episode list.
 */
@Composable
fun SeriesScreen(
    slug: String,
    navController: NavHostController
) {
    val app = App.instance
    var info by remember { mutableStateOf<SeasonInfo?>(null) }
    var error by remember { mutableStateOf(false) }
    var loadKey by remember { mutableStateOf(0) }
    var selectedSeason by rememberSaveable { mutableStateOf(0) }
    var episodes by remember { mutableStateOf<List<Episode>?>(null) }
    var epError by remember { mutableStateOf(false) }
    var epLoadKey by remember { mutableStateOf(0) }

    LaunchedEffect(slug, loadKey) {
        error = false
        episodes = null
        selectedSeason = 0
        try {
            info = StardimaApi.seasons(app.http, slug)
        } catch (e: Exception) {
            error = true
        }
    }

    // Load episodes when season selection changes (or on retry)
    LaunchedEffect(info?.seasons, selectedSeason, epLoadKey) {
        val info = info ?: return@LaunchedEffect
        if (info.seasons.isEmpty()) return@LaunchedEffect
        episodes = null
        epError = false
        try {
            episodes = StardimaApi.episodes(app.http, info.seasons[selectedSeason])
        } catch (e: Exception) {
            epError = true
        }
    }

    val infoData = info
    if (error && infoData == null) {
        ErrorBox(
            message = androidx.compose.ui.res.stringResource(
                com.aionoid.aratoons.R.string.error_network
            ),
            onRetry = { loadKey++ },
            modifier = Modifier.fillMaxSize()
        )
        return
    }
    if (infoData == null) {
        LoadingBox(Modifier.fillMaxSize())
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Header: poster + title + description
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .aspectRatio(2f / 3f)
                        .background(Color(0xFF2A2D31), RoundedCornerShape(12.dp))
                ) {
                    if (infoData.poster.isNotEmpty()) {
                        AsyncImage(
                            model = infoData.poster,
                            contentDescription = infoData.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = infoData.title.ifEmpty { slug },
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (infoData.description.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = infoData.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Season tabs
        if (infoData.seasons.isNotEmpty()) {
            item {
                TabRow(
                    selectedTabIndex = selectedSeason,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp)
                ) {
                    infoData.seasons.forEachIndexed { index, season ->
                        Tab(
                            selected = index == selectedSeason,
                            onFocus = {},
                            onClick = { selectedSeason = index },
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .pointerInput(index) {
                                    // tv-material 1.0.1's tvSelectable only handles D-pad enter
                                    detectTapGestures(onTap = { selectedSeason = index })
                                }
                        ) {
                            Text(
                                text = "Season $season",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }

        // Episodes
        val eps = episodes
        if (epError && eps == null) {
            item {
                ErrorBox(
                    message = androidx.compose.ui.res.stringResource(
                        com.aionoid.aratoons.R.string.error_generic
                    ),
                    onRetry = { epLoadKey++ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp)
                )
            }
        } else if (eps == null) {
            item {
                LoadingBox(Modifier.fillMaxWidth().padding(vertical = 60.dp))
            }
        } else if (eps.isEmpty()) {
            item {
                Text(
                    text = androidx.compose.ui.res.stringResource(
                        com.aionoid.aratoons.R.string.no_results
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(eps, key = { it.id }) { ep ->
                val onEpClick: () -> Unit = {
                    navController.navigate(
                        Routes.servers(
                            "/play/${ep.id}",
                            ep.title.ifEmpty { "${infoData.title} — ${ep.title}" },
                            infoData.poster
                        )
                    )
                }
                ListItem(
                    selected = false,
                    onClick = onEpClick,
                    headlineContent = {
                        Text(
                            text = ep.title,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ep.title.substringBefore(" ").ifEmpty {
                                    (eps.indexOf(ep) + 1).toString()
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 2.dp)
                        .pointerInput(ep.id) {
                            // tv-material 1.0.1's tvSelectable only handles D-pad enter
                            detectTapGestures(onTap = { onEpClick() })
                        }
                )
            }
        }
    }
}

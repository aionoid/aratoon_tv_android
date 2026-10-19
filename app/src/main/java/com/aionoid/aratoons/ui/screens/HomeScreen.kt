package com.aionoid.aratoons.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.aionoid.aratoons.App
import com.aionoid.aratoons.R
import com.aionoid.aratoons.data.RecentlyWatched
import com.aionoid.aratoons.data.StardimaApi
import com.aionoid.aratoons.data.VideoItem
import com.aionoid.aratoons.ui.MenuCard
import com.aionoid.aratoons.ui.PosterCard
import com.aionoid.aratoons.ui.Routes
import com.aionoid.aratoons.ui.SectionHeader
import com.aionoid.aratoons.ui.WideCard
import com.aionoid.aratoons.ui.LoadingBox
import com.aionoid.aratoons.ui.ErrorBox
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(navController: NavHostController) {
    val app = App.instance
    var episodes by remember { mutableStateOf<List<VideoItem>?>(null) }
    var movies by remember { mutableStateOf<List<VideoItem>?>(null) }
    var recent by remember { mutableStateOf<List<RecentlyWatched.Entry>?>(null) }
    var error by remember { mutableStateOf(false) }
    var loadKey by remember { mutableStateOf(0) }

    // Hoisted so they can be used inside non-composable click lambdas
    val moviesLabel = stringResource(R.string.movies)
    val seriesLabel = stringResource(R.string.series)
    val latestLabel = stringResource(R.string.latest)
    val searchLabel = stringResource(R.string.search)
    val recentLabel = stringResource(R.string.recently_watched)
    val settingsLabel = stringResource(R.string.settings)
    val newEpisodesLabel = stringResource(R.string.new_episodes)
    val newMoviesLabel = stringResource(R.string.new_movies)

    LaunchedEffect(loadKey) {
        error = false
        try {
            val (ep, mov, _) = StardimaApi.newReleases(app.http)
            episodes = ep
            movies = mov
        } catch (e: Exception) {
            error = true
        }
        recent = RecentlyWatched.load(app)
    }

    if (error && episodes == null) {
        ErrorBox(
            message = stringResource(R.string.error_network),
            onRetry = { loadKey++ },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
            ) {
                MenuCard(
                    label = moviesLabel,
                    icon = { tint ->
                        Icon(Icons.Filled.Movie, null, tint = tint, modifier = Modifier.size(30.dp))
                    },
                    onClick = {
                        navController.navigate(
                            Routes.catalog("/aflam", moviesLabel)
                        )
                    }
                )
                MenuCard(
                    label = seriesLabel,
                    icon = { tint ->
                        Icon(Icons.Filled.AutoAwesome, null, tint = tint, modifier = Modifier.size(30.dp))
                    },
                    onClick = {
                        navController.navigate(
                            Routes.catalog("/mosalsalat", seriesLabel)
                        )
                    }
                )
                MenuCard(
                    label = latestLabel,
                    icon = { tint ->
                        Icon(Icons.Filled.LiveTv, null, tint = tint, modifier = Modifier.size(30.dp))
                    },
                    onClick = {
                        navController.navigate(
                            Routes.catalog("/newrelases", latestLabel)
                        )
                    }
                )
                MenuCard(
                    label = searchLabel,
                    icon = { tint ->
                        Icon(Icons.Filled.Search, null, tint = tint, modifier = Modifier.size(30.dp))
                    },
                    onClick = { navController.navigate(Routes.SEARCH) }
                )
                if (!recent.isNullOrEmpty()) {
                    MenuCard(
                        label = recentLabel,
                        icon = { tint ->
                            Icon(Icons.Filled.History, null, tint = tint, modifier = Modifier.size(30.dp))
                        },
                        onClick = { /* row below */ }
                    )
                }
                MenuCard(
                    label = settingsLabel,
                    icon = { tint ->
                        Icon(Icons.Filled.Settings, null, tint = tint, modifier = Modifier.size(30.dp))
                    },
                    onClick = { navController.navigate(Routes.SETTINGS) }
                )
            }
        }

        recent?.takeIf { it.isNotEmpty() }?.let { entries ->
            item { SectionHeader(recentLabel) }
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(entries) { entry ->
                        WideCard(
                            title = entry.title,
                            subtitle = formatTime(entry.time),
                            posterUrl = entry.poster,
                            onClick = {
                                navController.navigate(
                                    Routes.player(entry.url, entry.title, entry.poster)
                                )
                            }
                        )
                    }
                }
            }
        }

        episodes?.takeIf { it.isNotEmpty() }?.let { eps ->
            item { SectionHeader(newEpisodesLabel) }
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(eps) { item ->
                        PosterCard(item = item, onClick = { onVideoClick(item, navController) })
                    }
                }
            }
        }

        movies?.takeIf { it.isNotEmpty() }?.let { movs ->
            item { SectionHeader(newMoviesLabel) }
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(movs) { item ->
                        PosterCard(item = item, onClick = { onVideoClick(item, navController) })
                    }
                }
            }
        }
    }
}

private fun onVideoClick(item: VideoItem, navController: NavHostController) {
    if (item.isSeries) {
        val slug = item.url.substringAfterLast("/")
        navController.navigate(Routes.series(slug))
    } else {
        navController.navigate(Routes.player(item.url, item.title, item.posterUrl))
    }
}

private fun formatTime(time: Long): String {
    if (time == 0L) return ""
    val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return fmt.format(Date(time))
}

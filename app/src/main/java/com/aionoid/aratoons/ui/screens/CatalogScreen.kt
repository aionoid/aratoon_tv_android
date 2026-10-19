package com.aionoid.aratoons.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.aionoid.aratoons.App
import com.aionoid.aratoons.data.StardimaApi
import com.aionoid.aratoons.data.VideoItem
import com.aionoid.aratoons.ui.ErrorBox
import com.aionoid.aratoons.ui.LoadingBox
import com.aionoid.aratoons.ui.PosterCard
import com.aionoid.aratoons.ui.Routes
import com.aionoid.aratoons.ui.ScreenTitle

/**
 * Catalog browsing (movies / series / latest) with language filters and
 * an infinite poster grid.
 */
@Composable
fun CatalogScreen(
    path: String,
    title: String,
    navController: NavHostController
) {
    val app = App.instance
    // Only show language filters for the main movie/series catalogs
    val showFilters = path == "/aflam" || path == "/mosalsalat"

    var filter by rememberSaveable { mutableStateOf(0) } // 0=all 1=sub 2=dub
    var items by remember { mutableStateOf<List<VideoItem>?>(null) }
    var page by remember { mutableStateOf(1) }
    var lastPage by remember { mutableStateOf(1) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var loadKey by remember { mutableStateOf(0) }
    val gridState = rememberLazyGridState()

    fun filterPath(): String = when (filter) {
        1 -> "$path?language=sub"
        2 -> "$path?language=dub"
        else -> path
    }

    // First page
    LaunchedEffect(filter, loadKey) {
        items = null
        error = false
        page = 1
        try {
            val (list, lp) = StardimaApi.catalog(app.http, filterPath(), 1)
            items = list
            lastPage = lp
        } catch (e: Exception) {
            error = true
        }
    }

    val list = items ?: emptyList()

    // Infinite scroll: load next page when approaching the end
    LaunchedEffect(gridState, list.size) {
        val visible = gridState.layoutInfo.visibleItemsInfo
        if (visible.isNotEmpty() &&
            visible.last().index >= list.size - 6 &&
            page < lastPage &&
            !loadingMore &&
            list.isNotEmpty()
        ) {
            loadingMore = true
            try {
                val (more, _) = StardimaApi.catalog(app.http, filterPath(), page + 1)
                val merged = list.toMutableList()
                val seen = merged.mapTo(mutableSetOf()) { it.url + it.title }
                for (m in more) {
                    val key = m.url + m.title
                    if (key !in seen) {
                        seen.add(key)
                        merged.add(m)
                    }
                }
                items = merged
                page++
            } catch (e: Exception) {
                // ignore: retry on next scroll
            } finally {
                loadingMore = false
            }
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScreenTitle(title)
        }

        if (showFilters) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterChip(
                        text = androidx.compose.ui.res.stringResource(
                            com.aionoid.aratoons.R.string.all
                        ),
                        selected = filter == 0,
                        onClick = { filter = 0 }
                    )
                    FilterChip(
                        text = androidx.compose.ui.res.stringResource(
                            com.aionoid.aratoons.R.string.subtitled
                        ),
                        selected = filter == 1,
                        onClick = { filter = 1 }
                    )
                    FilterChip(
                        text = androidx.compose.ui.res.stringResource(
                            com.aionoid.aratoons.R.string.dubbed
                        ),
                        selected = filter == 2,
                        onClick = { filter = 2 }
                    )
                }
            }
        }

        if (error && items == null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ErrorBox(
                    message = androidx.compose.ui.res.stringResource(
                        com.aionoid.aratoons.R.string.error_network
                    ),
                    onRetry = { loadKey++ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp)
                )
            }
        } else if (items == null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LoadingBox(Modifier.fillMaxWidth().padding(vertical = 80.dp))
            }
        } else if (list.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = androidx.compose.ui.res.stringResource(
                        com.aionoid.aratoons.R.string.no_results
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 80.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(list, key = { it.url + it.title + it.id }) { item ->
            PosterCard(item = item, onClick = {
                if (item.isSeries) {
                    navController.navigate(
                        Routes.series(item.url.substringAfterLast("/"))
                    )
                } else {
                    navController.navigate(
                        Routes.player(item.url, item.title, item.posterUrl)
                    )
                }
            })
        }
    }
}

/**
 * A selectable filter chip (M3).
 */
@Composable
fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val focusRequester = remember { FocusRequester() }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bg,
        contentColor = fg,
        modifier = modifier
            .focusRequester(focusRequester)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}

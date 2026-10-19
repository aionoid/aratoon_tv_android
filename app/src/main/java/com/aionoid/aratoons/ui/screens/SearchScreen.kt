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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

/**
 * Search screen: text field + results grid with pagination.
 */
@Composable
fun SearchScreen(navController: NavHostController) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var query by rememberSaveable { mutableStateOf("") }
    var searchedQuery by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<VideoItem>?>(null) }
    var page by remember { mutableStateOf(1) }
    var lastPage by remember { mutableStateOf(1) }
    var loadingMore by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var loadKey by remember { mutableStateOf(0) }
    val gridState = rememberLazyGridState()

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty() || searching) return
        searching = true
        items = null
        error = false
        page = 1
        searchedQuery = q
        scope.launch {
            try {
                val (list, lp) = StardimaApi.search(app.http, q, 1)
                items = list
                lastPage = lp
            } catch (e: Exception) {
                error = true
            } finally {
                searching = false
            }
        }
    }

    // Auto-search on Enter is handled by the button; also watch loadKey (retry)
    LaunchedEffect(searchedQuery, loadKey) {
        if (searchedQuery.isNotEmpty() && items == null && !searching && !error) {
            doSearch()
        }
    }

    val list = items ?: emptyList()

    // Infinite scroll
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
                val (more, _) = StardimaApi.search(app.http, searchedQuery, page + 1)
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
                // ignore
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            androidx.compose.ui.res.stringResource(
                                com.aionoid.aratoons.R.string.search_hint
                            )
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                )
                Button(
                    onClick = { doSearch() },
                    enabled = !searching
                ) {
                Text(
                    if (searching) {
                        androidx.compose.ui.res.stringResource(
                            com.aionoid.aratoons.R.string.searching
                        )
                    } else {
                        androidx.compose.ui.res.stringResource(
                            com.aionoid.aratoons.R.string.search
                        )
                    }
                )
                }
            }
        }

        if (searching) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LoadingBox(Modifier.fillMaxWidth().padding(vertical = 80.dp))
            }
        } else if (error && items == null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ErrorBox(
                    message = androidx.compose.ui.res.stringResource(
                        com.aionoid.aratoons.R.string.error_generic
                    ),
                    onRetry = {
                        items = null
                        loadKey++
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp)
                )
            }
        } else if (searchedQuery.isNotEmpty() && list.isEmpty() && items != null) {
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

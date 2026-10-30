package com.aionoid.aratoons.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.aionoid.aratoons.App
import com.aionoid.aratoons.R
import com.aionoid.aratoons.data.StardimaApi
import com.aionoid.aratoons.data.VideoItem
import com.aionoid.aratoons.data.VideoItemRouting
import com.aionoid.aratoons.ui.ErrorBox
import com.aionoid.aratoons.ui.LoadingBox
import com.aionoid.aratoons.ui.CatalogPageBar
import com.aionoid.aratoons.ui.PosterCard
import com.aionoid.aratoons.ui.Routes
import com.aionoid.aratoons.ui.systemBarsContentPadding
import kotlinx.coroutines.launch

/**
 * Search screen: text field + results grid with explicit pagination
 * (previous/next + page picker — same pattern as the catalogs).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(navController: NavHostController, initialQuery: String = "", focusEntry: FocusRequester? = null) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    val focusRequester = focusEntry ?: remember { FocusRequester() }

    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var searchedQuery by remember { mutableStateOf("") }
    var page by rememberSaveable { mutableStateOf(1) }
    var lastPage by remember { mutableStateOf(1) }
    var items by remember { mutableStateOf<List<VideoItem>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var loadKey by remember { mutableStateOf(0) }
    val gridState = rememberLazyGridState()

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        searchedQuery = q
        page = 1
    }

    LaunchedEffect(searchedQuery, page, loadKey) {
        if (searchedQuery.isEmpty()) return@LaunchedEffect
        searching = true
        error = false
        items = null
        try {
            val (list, lp) = StardimaApi.search(app.http, searchedQuery, page)
            items = list
            lastPage = lp
        } catch (e: Exception) {
            error = true
        } finally {
            searching = false
        }
    }

    fun goPage(n: Int) {
        val clamped = n.coerceIn(1, lastPage)
        if (clamped != page) {
            page = clamped
            scope.launch { gridState.scrollToItem(0) }
        }
    }

    val list = items ?: emptyList()

    // Run a query that arrived as an initial query, if any. Focus is
    // managed by the shell via focusEntry; this screen does not auto-request
    // focus on its own.
    LaunchedEffect(Unit) {
        if (initialQuery.trim().isNotEmpty()) doSearch()
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = systemBarsContentPadding(
            start = 16.dp,
            top = 8.dp,
            end = 16.dp,
            bottom = 16.dp
        ),
        modifier = Modifier
            .fillMaxSize()
            .focusRestorer()
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
                        Text(stringResource(R.string.search_hint))
                    },
                    singleLine = true,
                    // Enter / the soft keyboard's search action runs the
                    // search — previously only the button did.
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { doSearch() },
                        onDone = { doSearch() }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .background(
                            Color.White.copy(alpha = 0.06f),
                            RoundedCornerShape(14.dp)
                        ),
                    shape = RoundedCornerShape(14.dp)
                )
                Button(
                    onClick = { doSearch() },
                    enabled = !searching
                ) {
                    Text(
                        if (searching) {
                            stringResource(R.string.searching)
                        } else {
                            stringResource(R.string.search)
                        }
                    )
                }
            }
        }

        if (searchedQuery.isNotEmpty() && lastPage > 1) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                CatalogPageBar(
                    page = page,
                    lastPage = lastPage,
                    onPrev = { goPage(page - 1) },
                    onNext = { goPage(page + 1) },
                    onPage = { goPage(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                )
            }
        }

        if (searching) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LoadingBox(Modifier.fillMaxWidth().padding(vertical = 80.dp))
            }
        } else if (error && items == null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ErrorBox(
                    message = stringResource(R.string.error_generic),
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
                    text = stringResource(R.string.no_results),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 80.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(list, key = { it.url + it.title + it.id }) { item ->
            PosterCard(
                item = item,
                onClick = {
                    if (VideoItemRouting.isSeriesPage(item)) {
                        navController.navigate(
                            Routes.series(item.url.substringAfterLast("/"))
                        )
                    } else {
                        navController.navigate(
                            Routes.servers(item.url, item.title, item.posterUrl)
                        )
                    }
                },
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
            )
        }
    }
}

package com.aionoid.aratoons.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.aionoid.aratoons.App
import com.aionoid.aratoons.R
import com.aionoid.aratoons.data.StardimaApi
import com.aionoid.aratoons.data.VideoItem
import com.aionoid.aratoons.ui.ErrorBox
import com.aionoid.aratoons.ui.LoadingBox
import com.aionoid.aratoons.ui.PosterCard
import com.aionoid.aratoons.ui.Routes
import kotlinx.coroutines.launch

/**
 * Catalog browsing (movies / series / latest) with language filters and
 * explicit pagination: previous/next buttons + a page-number picker.
 * The server serves 15 items per page (last_page comes from the API).
 */
@Composable
fun CatalogScreen(
    path: String,
    title: String,
    navController: NavHostController
) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    // Only show language filters for the main movie/series catalogs
    val showFilters = path == "/aflam" || path == "/mosalsalat"

    var filter by rememberSaveable { mutableStateOf(0) } // 0=all 1=sub 2=dub
    var page by rememberSaveable { mutableStateOf(1) }
    var lastPage by remember { mutableStateOf(1) }
    var items by remember { mutableStateOf<List<VideoItem>?>(null) }
    var error by remember { mutableStateOf(false) }
    var loadKey by remember { mutableStateOf(0) }
    val gridState = rememberLazyGridState()

    fun filterPath(): String = when (filter) {
        1 -> "$path?language=sub"
        2 -> "$path?language=dub"
        else -> path
    }

    // Changing the filter always returns to page 1
    LaunchedEffect(filter) { page = 1 }

    LaunchedEffect(filter, page, loadKey) {
        items = null
        error = false
        try {
            val (list, lp) = StardimaApi.catalog(app.http, filterPath(), page)
            items = list
            lastPage = lp
        } catch (e: Exception) {
            error = true
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

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
                )
                if (lastPage > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = { goPage(page - 1) }, enabled = page > 1) {
                            Text(stringResource(R.string.page_previous))
                        }
                        PageDropdown(
                            current = page,
                            lastPage = lastPage,
                            onPick = { goPage(it) },
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { goPage(page + 1) }, enabled = page < lastPage) {
                            Text(stringResource(R.string.page_next))
                        }
                    }
                }
            }
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
                        text = stringResource(R.string.all),
                        selected = filter == 0,
                        onClick = { filter = 0 }
                    )
                    FilterChip(
                        text = stringResource(R.string.subtitled),
                        selected = filter == 1,
                        onClick = { filter = 1 }
                    )
                    FilterChip(
                        text = stringResource(R.string.dubbed),
                        selected = filter == 2,
                        onClick = { filter = 2 }
                    )
                }
            }
        }

        if (error && items == null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ErrorBox(
                    message = stringResource(R.string.error_network),
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
            PosterCard(item = item, onClick = {
                if (item.isSeries) {
                    navController.navigate(
                        Routes.series(item.url.substringAfterLast("/"))
                    )
                } else {
                    navController.navigate(
                        Routes.servers(item.url, item.title, item.posterUrl)
                    )
                }
            })
        }
    }
}

/**
 * "Page X / Y" picker: a read-only field that opens a scrollable menu of
 * page numbers. Shown when the catalog has more than one page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageDropdown(
    current: Int,
    lastPage: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = stringResource(R.string.page_indicator, current, lastPage),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            },
            modifier = Modifier
                .menuAnchor()
                .statusBarsPadding()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            for (p in 1..lastPage) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (p == current) {
                                stringResource(R.string.page_current, p)
                            } else {
                                stringResource(R.string.page_menu_item, p)
                            }
                        )
                    },
                    onClick = {
                        expanded = false
                        onPick(p)
                    }
                )
            }
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

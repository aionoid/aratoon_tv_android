package com.aionoid.aratoons.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.aionoid.aratoons.App
import com.aionoid.aratoons.R
import com.aionoid.aratoons.data.StardimaApi
import com.aionoid.aratoons.data.VideoItem
import com.aionoid.aratoons.ui.BlurredImage
import com.aionoid.aratoons.ui.ErrorBox
import com.aionoid.aratoons.ui.LoadingBox
import com.aionoid.aratoons.ui.Routes
import kotlinx.coroutines.launch

/**
 * Catalog screen for a Stardima path (all movies / subtitled / series).
 *
 * Two-pane layout: a large preview of the selected item on the left, a
 * list of rows (thumbnail + title + play) on the right, and a pagination
 * bar (previous / page picker / next) on top.
 *
 * Edge-to-edge: system bar insets are handled as explicit padding so the
 * list can scroll behind the navigation bar.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    path: String,
    title: String,
    navController: NavHostController
) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var page by rememberSaveable { mutableStateOf(1) }
    var lastPage by remember { mutableStateOf(1) }
    var items by remember { mutableStateOf<List<VideoItem>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var loadKey by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val firstRowFocus = remember { FocusRequester() }

    LaunchedEffect(path, page, loadKey) {
        loading = true
        error = false
        items = null
        selected = 0
        try {
            val (list, lp) = StardimaApi.catalog(app.http, path, page)
            items = list
            lastPage = lp
        } catch (e: Exception) {
            error = true
        } finally {
            loading = false
        }
    }

    fun goPage(n: Int) {
        val clamped = n.coerceIn(1, lastPage)
        if (clamped != page) {
            page = clamped
            scope.launch { listState.scrollToItem(0) }
        }
    }

    val list = items ?: emptyList()
    val preview = list.getOrNull(selected)

    // Leanback: start focus on the first row of the loaded page
    LaunchedEffect(items) {
        items?.takeIf { it.isNotEmpty() }?.let { firstRowFocus.requestFocus() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar: title + pagination
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = statusTop + 12.dp,
                    bottom = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (lastPage > 1) {
                Button(onClick = { goPage(page - 1) }, enabled = page > 1) {
                    Text(stringResource(R.string.page_previous))
                }
                PageDropdown(
                    current = page,
                    lastPage = lastPage,
                    onPick = { goPage(it) },
                    modifier = Modifier.width(230.dp)
                )
                Button(onClick = { goPage(page + 1) }, enabled = page < lastPage) {
                    Text(stringResource(R.string.page_next))
                }
            }
        }

        // Two panes: preview (left) + list (right)
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Box(
                modifier = Modifier
                    .weight(0.56f)
                    .padding(start = 24.dp, end = 16.dp, bottom = navBottom + 20.dp)
            ) {
                if (preview != null) {
                    CatalogPreviewCard(
                        item = preview,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (!loading && !error) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(0.44f)
                    .fillMaxHeight()
                    .focusRestorer(),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 24.dp,
                    top = 4.dp,
                    bottom = navBottom + 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (loading) {
                    item {
                        LoadingBox(Modifier.fillMaxWidth().height(200.dp))
                    }
                } else if (error && items == null) {
                    item {
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
                } else if (list.isEmpty() && items != null) {
                    item {
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
                itemsIndexed(
                    list,
                    key = { _, item -> item.url + item.title + item.id }
                ) { index, item ->
                    CatalogRow(
                        item = item,
                        selected = index == selected,
                        onClick = {
                            if (item.isSeries) {
                                navController.navigate(
                                    Routes.series(item.url.substringAfterLast("/"))
                                )
                            } else {
                                navController.navigate(
                                    Routes.servers(item.url, item.title, item.posterUrl)
                                )
                            }
                        },
                        onFocus = { selected = index },
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstRowFocus)
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
    }
}

/**
 * Large preview card for the selected catalog item: the poster fills the
 * card, with a blurred copy of the image under the text zone.
 */
@Composable
fun CatalogPreviewCard(
    item: VideoItem,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .shadow(
                elevation = 20.dp,
                shape = cardShape,
                ambientColor = Color(0xFF8FB3D9).copy(alpha = 0.15f),
                spotColor = Color.Black.copy(alpha = 0.35f)
            )
            .background(Color(0xFF1A1C1E), cardShape)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.25f),
                shape = cardShape
            )
            .clip(cardShape)
    ) {
        if (item.posterUrl.isNotEmpty()) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            BlurredImage(
                model = item.posterUrl,
                modifier = Modifier.fillMaxSize(),
                rampStart = 0.5f,
                rampFull = 0.8f
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2A2D31))
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.year.isNotEmpty() || item.quality.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.year.isNotEmpty()) {
                        Text(
                            text = item.year,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    if (item.quality.isNotEmpty()) {
                        Text(
                            text = item.quality,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
            if (item.description.isNotEmpty()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/**
 * One row of the catalog list: square thumbnail, title + year/quality,
 * and a circular play affordance. The whole row is focusable; focusing it
 * updates the left preview.
 */
@Composable
fun CatalogRow(
    item: VideoItem,
    selected: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val unit = remember { Unit }
    val rowShape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(104.dp)
            .background(
                if (selected) Color.White.copy(alpha = 0.08f)
                else Color.White.copy(alpha = 0.04f),
                rowShape
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White.copy(alpha = 0.12f)
                },
                shape = rowShape
            )
            .clip(rowShape)
            .focusable()
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.hasFocus) onFocus() }
            .pointerInput(unit) {
                detectTapGestures(onTap = { onClick() })
            }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2A2D31))
        ) {
            if (item.posterUrl.isNotEmpty()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 14.dp, end = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.year.isNotEmpty() || item.quality.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.year.isNotEmpty()) {
                        Text(
                            text = item.year,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    if (item.quality.isNotEmpty()) {
                        Text(
                            text = item.quality,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else Color.White.copy(alpha = 0.12f)
                )
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    Color.White
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(22.dp)
            )
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
            modifier = Modifier.menuAnchor()
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

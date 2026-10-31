package com.aionoid.aratoons.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import coil.compose.AsyncImage
import com.aionoid.aratoons.App
import com.aionoid.aratoons.R
import com.aionoid.aratoons.data.StardimaApi
import com.aionoid.aratoons.data.VideoItem
import com.aionoid.aratoons.data.VideoItemRouting
import com.aionoid.aratoons.ui.ActionPill
import com.aionoid.aratoons.ui.BlurredImage
import com.aionoid.aratoons.ui.CatalogPageBar
import com.aionoid.aratoons.ui.ErrorBox
import com.aionoid.aratoons.ui.LoadingBox
import com.aionoid.aratoons.ui.PosterCard
import com.aionoid.aratoons.ui.Routes
import kotlinx.coroutines.launch

/**
 * Catalog screen for a Stardima path (movies / series) — TVMOON
 * glassmorphism layout (catalog_UI.png):
 *
 *  - Layer 0: full-screen blurred backdrop of the selected item's poster.
 *  - Top: glass pill pagination bar (prev / dots / page badge / dots /
 *    next) — the page number is a gold badge.
 *  - Left (7/12): featured spotlight — the poster as a dimmed panel
 *    background, a gold "Featured" badge + year/quality chip on top and
 *    a glass info card on the bottom (title, subtitle, description,
 *    Watch button).
 *  - Right (5/12): glass panel with a header (catalog title + "View all")
 *    and a scrollable list of horizontal cards (landscape thumbnail,
 *    title, meta, gold detail line, play affordance). Focusing a card
 *    updates the spotlight.
 */
@Composable
fun CatalogScreen(
    path: String,
    title: String,
    navController: NavHostController,
    focusEntry: FocusRequester? = null
) {
    val app = App.instance
    val scope = rememberCoroutineScope()

    var page by rememberSaveable { mutableStateOf(1) }
    var lastPage by remember { mutableStateOf(1) }
    var items by remember { mutableStateOf<List<VideoItem>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var loadKey by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf(0) }
    var gridMode by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val gridState = remember { androidx.compose.foundation.lazy.grid.LazyGridState() }
    val firstRowFocus = focusEntry ?: remember { FocusRequester() }
    val watchFocus = remember { FocusRequester() }

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
            android.util.Log.e("CatalogScreen", "load failed: $e", e)
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

    // Focus is managed by the shell via focusEntry; this screen does not
    // auto-request focus on its own.

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 0: full-screen blurred backdrop of the selected poster, plus a
        // vertical gradient scrim (dark top + bottom, lighter middle) so the
        // featured text stays legible and the low-res poster is hidden smoothly.
        if (preview?.posterUrl?.isNotEmpty() == true) {
            BlurredImage(
                model = preview.posterUrl,
                modifier = Modifier.fillMaxSize(),
                blurRadius = 40f,
                fullFrame = true,
                alpha = 0.55f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.85f),
                            0.4f to Color.Black.copy(alpha = 0.28f),
                            0.5f to Color.Black.copy(alpha = 0.12f),
                            0.6f to Color.Black.copy(alpha = 0.28f),
                            1.0f to Color.Black.copy(alpha = 0.85f)
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }

        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            // Top: tappable page-number bar (Flutter catalog style) + list/grid toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CatalogPageBar(
                    page = page,
                    lastPage = lastPage,
                    onPrev = { goPage(page - 1) },
                    onNext = { goPage(page + 1) },
                    onPage = { goPage(it) },
                    modifier = Modifier.weight(1f)
                )
                ActionPill(
                    label = stringResource(R.string.list_mode),
                    gold = !gridMode,
                    small = true,
                    onClick = {
                        gridMode = false
                        scope.launch { listState.scrollToItem(0) }
                    }
                )
                ActionPill(
                    label = stringResource(R.string.grid_mode),
                    gold = gridMode,
                    small = true,
                    onClick = {
                        gridMode = true
                        scope.launch { gridState.scrollToItem(0) }
                    }
                )
            }
            Spacer(Modifier.height(14.dp))

            if (gridMode) {
                // Grid mode (Flutter catalog): the poster grid, full width
                if (loading) {
                    LoadingBox(Modifier.fillMaxWidth().weight(1f))
                } else if (error && items == null) {
                    ErrorBox(
                        message = stringResource(R.string.error_generic),
                        onRetry = {
                            items = null
                            loadKey++
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        itemsIndexed(
                            list,
                            key = { _, item -> item.url + item.title + item.id }
                        ) { index, item ->
                            PosterCard(
                                item = item,
                                widthDp = 148.dp,
                                onClick = {
                                    if (VideoItemRouting.isSeriesPage(item)) {
                                        navController.navigate(
                                            Routes.series(item.url.substringAfterLast("/"))
                                        )
                                    } else {
                                        navController.navigate(
                                            Routes.movie(
                                                item.url,
                                                item.title,
                                                item.posterUrl,
                                                item.year,
                                                item.description,
                                                item.quality
                                            )
                                        )
                                    }
                                },
                                modifier = if (index == 0) {
                                    Modifier.focusRequester(firstRowFocus)
                                } else {
                                    Modifier
                                }
                            )
                        }
                    }
                }
            } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left (7/12): featured spotlight
                Box(modifier = Modifier.weight(0.585f).fillMaxHeight()) {
                    when {
                        preview != null -> CatalogSpotlight(
                            item = preview,
                            modifier = Modifier.fillMaxSize(),
                            watchFocusRequester = watchFocus,
                            onWatch = {
                                if (VideoItemRouting.isSeriesPage(preview)) {
                                    navController.navigate(
                                        Routes.series(preview.url.substringAfterLast("/"))
                                    )
                                } else {
                                    navController.navigate(
                                        Routes.movie(
                                            preview.url,
                                            preview.title,
                                            preview.posterUrl,
                                            preview.year,
                                            preview.description,
                                            preview.quality
                                        )
                                    )
                                }
                            }
                        )
                        !loading && !error -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(24.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                        )
                        else -> LoadingBox(Modifier.fillMaxSize())
                    }
                }

                // Right (5/12): list panel
                Column(
                    modifier = Modifier
                        .weight(0.415f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(24.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.view_all) + " >",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LazyColumn(
                        state = listState,
                        // No focusRestorer() here: it pins the focused row's
                        // pinnable item, and when a page change detaches that
                        // row the restorer releases it twice ("Release should
                        // only be called once"). Focus is re-anchored on the
                        // first row in LaunchedEffect(items) instead.
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (loading) {
                            item {
                                LoadingBox(Modifier.fillMaxWidth().height(160.dp))
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
                                        .padding(vertical = 60.dp),
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                        itemsIndexed(
                            list,
                            key = { _, item -> item.url + item.title + item.id }
                        ) { index, item ->
                            CatalogListItem(
                                item = item,
                                selected = index == selected,
                                onClick = {
                                    if (VideoItemRouting.isSeriesPage(item)) {
                                        navController.navigate(
                                            Routes.series(item.url.substringAfterLast("/"))
                                        )
                                    } else {
                                        navController.navigate(
                                            Routes.movie(
                                                item.url,
                                                item.title,
                                                item.posterUrl,
                                                item.year,
                                                item.description,
                                                item.quality
                                            )
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
        }
    }
}

/**
 * Featured spotlight (left panel of the catalog): the poster as a dimmed
 * panel background (HTML: opacity 60) with a bottom-up black gradient, a
 * gold "Featured" badge + year/quality chip on top, and a glass info
 * card (title, subtitle, description, Watch button) on the bottom.
 */
@Composable
fun CatalogSpotlight(
    item: VideoItem,
    onWatch: () -> Unit,
    modifier: Modifier = Modifier,
    watchFocusRequester: FocusRequester = FocusRequester()
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .shadow(
                elevation = 20.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.4f)
            )
            .background(Color(0xFF1A1C1E), shape)
            .border(1.dp, Color.White.copy(alpha = 0.2f), shape)
            .clip(shape)
    ) {
        // Poster as dimmed panel background (HTML: opacity 60)
        if (item.posterUrl.isNotEmpty()) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.6f,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2A2D31))
            )
        }
        // Bottom-up black gradient (HTML: from-black via-black/40 to-transparent)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = 0.4f),
                        1f to Color.Black.copy(alpha = 0.85f)
                    )
                )
        )
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            // Top row: "Featured" badge + year/quality chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.featured),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        ),
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (item.year.isNotEmpty() || item.quality.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = listOf(item.year, item.quality)
                                .filter { it.isNotEmpty() }
                                .joinToString(" • "),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            // Glass info card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.year.isNotEmpty() || item.quality.isNotEmpty()) {
                    Text(
                        text = listOf(item.year, item.quality)
                            .filter { it.isNotEmpty() }
                            .joinToString(" • "),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (item.description.isNotEmpty()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Button(
                    onClick = onWatch,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .align(Alignment.Start)
                        .focusRequester(watchFocusRequester),
                    shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        focusedContainerColor = MaterialTheme.colorScheme.primary,
                        focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                        pressedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(
                            if (item.isSeries) R.string.watch_episode else R.string.watch_now
                        ),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

/**
 * One row of the catalog list (right panel): landscape thumbnail,
 * title + meta + gold detail line, and a circular play affordance.
 * The whole row is focusable; focusing it updates the left spotlight.
 */
@Composable
fun CatalogListItem(
    item: VideoItem,
    selected: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.72f)
    )
    val meta = listOf(item.year, item.quality)
        .filter { it.isNotEmpty() }
        .joinToString(" • ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(shape)
            .background(
                if (selected) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.06f),
                shape
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White.copy(alpha = 0.10f)
                },
                shape = shape
            )
            // onFocusChanged before clickable() — it only aggregates focus
            // targets that follow it in the chain (see PosterCard)
            .onFocusChanged { if (it.hasFocus) onFocus() }
            // clickable() so D-pad CENTER opens the item
            .clickable(interactionSource = null, indication = null) { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Landscape thumbnail (4:3 crop of the portrait poster)
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF2A2D31), RoundedCornerShape(10.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (item.description.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        // Circular play affordance
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.White.copy(alpha = 0.1f)
                    },
                    CircleShape
                )
                .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    Color.White
                },
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

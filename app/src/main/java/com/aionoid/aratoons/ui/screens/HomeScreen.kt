package com.aionoid.aratoons.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import kotlin.collections.mutableMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aionoid.aratoons.App
import com.aionoid.aratoons.R
import com.aionoid.aratoons.data.RecentlyWatched
import com.aionoid.aratoons.data.StardimaApi
import com.aionoid.aratoons.data.VideoItem
import com.aionoid.aratoons.ui.ContentRail
import com.aionoid.aratoons.ui.ErrorBox
import com.aionoid.aratoons.ui.LoadingBox
import com.aionoid.aratoons.ui.Routes
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Home screen — immersive-list style
 * (developer.android.com/design/ui/tv/guides/components/immersive-list):
 *
 *  - Full-bleed preview area on top: cinematic background image + scrim and
 *    a content block (title + description). The
 *    preview is dynamic: while navigating the content rows below it, it
 *    automatically shows the currently focused item ("progressive
 *    disclosure").
 *  - Content rows below: Recently Watched (when non-empty), Popular,
 *    Movies, Series. The focused card is emphasized (scale + border + glow).
 *
 * D-pad (deterministic, no default nearest-element traversal):
 *  - Preview: ←/→ cycle the featured item; UP → tab bar; DOWN → first card
 *    of the top rail; ENTER opens the preview item.
 *  - Rows: ←/→ within a rail (ring wrap on the right edge); UP/DOWN between
 *    rails → same card index of the neighbouring rail (clamped); UP/LEFT at
 *    the top rail's leftmost card → preview; other rails' LEFT/UP → tab bar;
 *    DOWN on the bottom rail → consumed no-op; ENTER opens the item.
 */
@Composable
fun HomeScreen(
    navController: NavHostController,
    previewFocus: FocusRequester,
    tabBarEntry: FocusRequester
) {
    val app = App.instance
    val context = LocalContext.current

    var featured by remember { mutableStateOf<List<VideoItem>?>(null) }
    var recent by remember { mutableStateOf<List<VideoItem>?>(null) }
    var allMovies by remember { mutableStateOf<List<VideoItem>?>(null) }
    var allSeries by remember { mutableStateOf<List<VideoItem>?>(null) }
    var error by remember { mutableStateOf(false) }
    var loadKey by remember { mutableStateOf(0) }
    var heroIndex by rememberSaveable { mutableIntStateOf(0) }
    var focusedCard by remember { mutableStateOf<VideoItem?>(null) }

    val cardNodes = remember { mutableMapOf<Int, FocusRequester>() }
    val railStates = remember { mutableMapOf<Int, LazyListState>() }
    val railColumnState = remember { LazyListState() }

    LaunchedEffect(loadKey) {
        featured = null; recent = null; allMovies = null; allSeries = null
        error = false; heroIndex = 0
        try {
            // Recently watched comes from prefs — show it while network loads
            recent = RecentlyWatched.load(context).toItems()
            // Network: "الرائج" (Popular) carousel + first movies page +
            // first series page (rails)
            val (pop, moviesPage, seriesPage) = coroutineScope {
                val pJob = async { StardimaApi.popular(app.http) }
                val mJob = async { StardimaApi.catalog(app.http, "/aflam", 1) }
                val sJob = async { StardimaApi.catalog(app.http, "/mosalsalat", 1) }
                Triple(pJob.await(), mJob.await().first, sJob.await().first)
            }
            featured = pop
            allMovies = moviesPage
            allSeries = seriesPage
        } catch (e: Exception) {
            android.util.Log.e("HomeScreen", "load failed: $e", e)
            error = true
        }
    }

    val previewItem = focusedCard ?: featured?.getOrNull(heroIndex)
    val previewCount = featured?.size ?: 0

    // Featured carousel auto-advance (wraps around) — key on heroIndex so a
    // manual change restarts the interval.
    LaunchedEffect(featured, heroIndex) {
        if (previewCount > 1) {
            delay(7000)
            heroIndex = (heroIndex + 1) % previewCount
        }
    }

    // Focus is owned by the shell (AraTooonsShell): it requests the preview
    // when the user presses DOWN/OK from the Home tab, and requests the tab
    // when a tab is selected. This screen does NOT auto-request focus, so
    // switching away and back never fights for focus or leaves a stale anchor.

    fun movieOnClick(it: VideoItem) {
        navController.navigate(
            Routes.movie(
                it.url,
                it.title,
                it.posterUrl,
                it.year,
                it.description,
                it.quality
            )
        )
    }

    fun seriesOnClick(it: VideoItem) {
        navController.navigate(Routes.series(it.url.substringAfterLast("/")))
    }

    fun recentOnClick(it: VideoItem) {
        when {
            it.url.contains("/play/") -> movieOnClick(it) // replay the episode
            it.url.contains("/tvshow/") -> seriesOnClick(it)
            else -> movieOnClick(it)
        }
    }

    // Rails: Recently (if any), Popular, Movies, Series. Handlers are built
    // in parallel with the rails (the list is conditional, so a fixed
    // handler list would misalign when Recently is absent).
    val rails = buildList {
        val rec = recent
        if (rec != null && rec.isNotEmpty()) add(stringResource(R.string.recently_watched) to rec)
        val feat = featured
        if (feat != null && feat.isNotEmpty()) add(stringResource(R.string.popular) to feat)
        val mov = allMovies
        if (mov != null && mov.isNotEmpty()) add(stringResource(R.string.movies) to mov)
        val ser = allSeries
        if (ser != null && ser.isNotEmpty()) add(stringResource(R.string.series) to ser)
    }
    val railHandlers: List<(VideoItem) -> Unit> = buildList {
        val rec = recent
        if (rec != null && rec.isNotEmpty()) add({ item -> recentOnClick(item) })
        val feat = featured
        if (feat != null && feat.isNotEmpty()) add({ item: VideoItem ->
            if (item.isSeries) seriesOnClick(item) else movieOnClick(item)
        })
        val mov = allMovies
        if (mov != null && mov.isNotEmpty()) add({ item -> movieOnClick(item) })
        val ser = allSeries
        if (ser != null && ser.isNotEmpty()) add({ item -> seriesOnClick(item) })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Immersive preview: full width, flush to the top of the content
            // area — right under the tab bar, no gap. The preview area is a
            // fixed-height strip at the top of the screen (immersive-list
            // anatomy), ~42% of the home viewport, clamped.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val previewH = (maxHeight * 0.42f).coerceIn(200.dp, 360.dp)
                Box(
                    modifier = Modifier
                        .height(previewH)
                        // Clip the preview to its bounds: the 1.45x over-crop
                        // of the background (per the Android TV "Composition"
                        // guideline) is otherwise drawn outside the box —
                        // Compose does not clip graphicsLayer overflow. A zero
                        // corner radius is a plain rectangle clip — the preview
                        // is full-bleed and flush to the screen edges.
                        .clip(RoundedCornerShape(0.dp))
                ) {
                    ImmersivePreview(
                        item = previewItem,
                        count = previewCount,
                        index = heroIndex,
                        previewFocus = previewFocus,
                        tabBarEntry = tabBarEntry,
                        getFirstCard = { cardNodes[0] },
                        onOpen = { it },
                        onCycleLeft = { heroIndex = heroIndex - 1 },
                        onCycleRight = { heroIndex = (heroIndex + 1) },
                        modifier = Modifier.fillMaxSize()
                    )
                    // Loading / error overlays
                    if (featured == null && !error) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingBox(Modifier.size(48.dp))
                        }
                    }
                    if (error && featured == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ErrorBox(
                                message = stringResource(R.string.error_generic),
                                onRetry = { loadKey++ }
                            )
                        }
                    }
                }
            }
            // Rails in one vertical scroll (same as the old home); the 20dp
            // padding that used to come from the outer Column is now on the
            // list itself.
            LazyColumn(
                state = railColumnState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                rails.forEachIndexed { railIdx, (railTitle, railItems) ->
                    item {
                        ContentRail(
                            title = railTitle,
                            items = railItems,
                            onItem = railHandlers[railIdx],
                            railIndex = railIdx,
                            railCount = rails.size,
                            prevCount = rails.getOrNull(railIdx - 1)?.second?.size ?: 0,
                            nextCount = rails.getOrNull(railIdx + 1)?.second?.size ?: 0,
                            previewFocus = previewFocus,
                            navEntry = tabBarEntry,
                            cardNodes = cardNodes,
                            railStates = railStates,
                            railColumnState = railColumnState,
                            onCardFocus = { item, hasFocus ->
                                if (hasFocus) {
                                    // Preview follows the focused card
                                    focusedCard = item
                                } else if (focusedCard == item) {
                                    focusedCard = null
                                    // When the preview becomes visible again,
                                    // align the featured carousel with the
                                    // item the user was last looking at (if it
                                    // is part of the featured list).
                                    featured?.indexOfFirst { it.url == item.url }
                                        ?.let { i -> heroIndex = i }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Immersive preview area: full-bleed background image with cinematic
 * scrim (per the Android TV immersive-list anatomy), a content block on the
 * left (title + description). The whole area is a
 * single focus target (ENTER/OK opens the preview item).
 */
@Composable
private fun ImmersivePreview(
    item: VideoItem?,
    count: Int,
    index: Int,
    previewFocus: FocusRequester,
    tabBarEntry: FocusRequester,
    getFirstCard: () -> FocusRequester?,
    onOpen: (VideoItem) -> Unit,
    onCycleLeft: () -> Unit,
    onCycleRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gold = MaterialTheme.colorScheme.primary
    var blockFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (blockFocused) 1.01f else 1f,
        animationSpec = spring(dampingRatio = 0.72f)
    )

    val bgUrl = when {
        item?.backgroundUrl?.isNotEmpty() == true -> item.backgroundUrl
        item?.posterUrl?.isNotEmpty() == true -> StardimaApi.upscalePoster(item.posterUrl)
        else -> ""
    }

    BoxWithConstraints(modifier = modifier) {
        // Capture the container size once (in dp) so the background shift can
        // use it inside the GraphicsLayerEffect block below.
        val bgW = maxWidth.value
        val bgH = maxHeight.value
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .scale(scale)
                .onFocusChanged { blockFocused = it.hasFocus }
                .focusable()
                .focusRequester(previewFocus)
                .clickable(interactionSource = null, indication = null) {
                    item?.let { onOpen(it) }
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val titleSnippet = item?.title?.take(24) ?: "n/a"
                    android.util.Log.d("FocusMap", "PREVIEW key=${event.key} item=${titleSnippet}")
                    when (event.key) {
                        Key.DirectionUp -> {
                            tabBarEntry.requestFocus()
                            true
                        }
                        Key.DirectionDown -> {
                            getFirstCard()?.let { node ->
                                try { node.requestFocus() } catch (e: IllegalStateException) {}
                            }
                            true
                        }
                        Key.DirectionLeft -> {
                            if (index > 0) { onCycleLeft(); true } else {
                                tabBarEntry.requestFocus()
                                true
                            }
                        }
                        Key.DirectionRight -> {
                            if (index < count - 1) { onCycleRight(); true } else {
                                tabBarEntry.requestFocus()
                                true
                            }
                        }
                        else -> false
                    }
                }
        ) {
            if (bgUrl.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(bgUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    // Immersive-list "Composition" (developer.android.com): scale
                    // up and shift the banner so the subject lands in the
                    // top-right (cinematic), instead of a centered full-crop that
                    // hides the subject under the content block.
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.45f
                            scaleY = 1.45f
                            // Shift right AND up so the subject lands in the
                            // top-right corner (away from the top-left content
                            // block), per the Android TV Composition guideline.
                            translationX = +(0.22f * bgW)
                            translationY = -(0.12f * bgH)
                        }
                )
            // Cinematic scrim: dark from the left for text legibility, fading
            // out to the right; bottom fade so the preview blends into the
            // rows below.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.92f),
                            0.45f to Color.Black.copy(alpha = 0.55f),
                            1f to Color.Black.copy(alpha = 0.12f)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.05f),
                                Color.Black.copy(alpha = 0.0f),
                                Color.Black.copy(alpha = 0.0f),
                                Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.20f),
                                Color.Black.copy(alpha = 0.45f)
                            )
                        )
                    )
            )
        }

        // Content block (title, description, CTAs, dots)
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp)
        ) {
            if (item != null) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.description.isNotEmpty()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .widthIn(max = 640.dp)
                    )
                }
            }
        }
    }
}
}

/**
 * Recently watched prefs entries -> VideoItems for the rails.
 */
private fun List<RecentlyWatched.Entry>.toItems(): List<VideoItem> = map { e ->
    VideoItem(
        id = 0,
        title = e.title,
        url = e.url,
        posterUrl = e.poster,
        isSeries = e.url.contains("/tvshow/") && !e.url.contains("/play/"),
        year = "",
        description = "",
        quality = ""
    )
}

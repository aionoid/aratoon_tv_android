package com.aionoid.aratoons.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.aionoid.aratoons.data.RecentlyWatched
import com.aionoid.aratoons.data.StardimaApi
import com.aionoid.aratoons.data.VideoItem
import com.aionoid.aratoons.ui.BlurredImage
import com.aionoid.aratoons.ui.ContentRail
import com.aionoid.aratoons.ui.ErrorBox
import com.aionoid.aratoons.ui.LoadingBox
import com.aionoid.aratoons.ui.Routes
import com.aionoid.aratoons.ui.SideNavOverlay
import com.aionoid.aratoons.ui.TopBarOverlay
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Home screen — full UI per the TVMOON hero_section.jpg reference:
 *
 *  - Layer 0: the selected hero's poster, blurred, filling the ENTIRE
 *    screen (behind everything).
 *  - Layer 1: a floating rounded panel (inset 24dp sides / 40dp top /
 *    16dp bottom, 2dp white border) containing:
 *      * the sharp hero image as the panel background
 *      * left→right + bottom black gradients for text legibility
 *      * transparent top bar (hamburger pill, logo, search pill)
 *      * transparent side nav overlay (Home/Movies/Series/Search +
 *        Settings pinned bottom) — replaces the old opaque sidebar
 *      * hero content (title, description, Watch now, +, page dots)
 *        then the rails — Popular, Recently Watched, Movies, Series —
 *        in one vertical scroll.
 *
 * D-pad ← / → on the hero switches the featured item (and with it the
 * full-screen background).
 */
@Composable
fun HomeScreen(navController: NavHostController) {
    val app = App.instance
    val context = LocalContext.current

    var featured by remember { mutableStateOf<List<VideoItem>?>(null) }
    var recent by remember { mutableStateOf<List<VideoItem>?>(null) }
    var allMovies by remember { mutableStateOf<List<VideoItem>?>(null) }
    var allSeries by remember { mutableStateOf<List<VideoItem>?>(null) }
    var error by remember { mutableStateOf(false) }
    var loadKey by remember { mutableStateOf(0) }
    var heroIndex by rememberSaveable { mutableStateOf(0) }
    var showNav by rememberSaveable { mutableStateOf(true) }

    val heroFocus = remember { FocusRequester() }

    LaunchedEffect(loadKey) {
        featured = null; recent = null; allMovies = null; allSeries = null
        error = false; heroIndex = 0
        try {
            // Recently watched comes from prefs — show it while network loads
            recent = RecentlyWatched.load(context).toItems()
            // Network: "الرائج" (Popular) home-page carousel + first catalog pages
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

    // Leanback: start focus on the hero once data is in (D-pad ←/→ switches)
    LaunchedEffect(featured) {
        if (featured != null && featured!!.isNotEmpty()) heroFocus.requestFocus()
    }

    val moviesLabel = stringResource(R.string.movies)
    val seriesLabel = stringResource(R.string.series)
    val hero = featured?.getOrNull(heroIndex)
    val heroCount = featured?.size ?: 0

    fun movieOnClick(it: VideoItem) {
        navController.navigate(Routes.servers(it.url, it.title, it.posterUrl))
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

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 0: full-screen blurred hero background
        if (hero?.posterUrl?.isNotEmpty() == true) {
            BlurredImage(
                model = hero.posterUrl,
                modifier = Modifier.fillMaxSize(),
                blurRadius = 24f,
                fullFrame = true,
                alpha = 0.55f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }

        // Layer 1: full-screen panel — white border hugs the screen edge
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, Color.White, RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Sharp hero image (cover)
            if (hero?.posterUrl?.isNotEmpty() == true) {
                AsyncImage(
                    model = hero.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Left-to-right dark gradient (hero text legibility)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.75f),
                            0.35f to Color.Black.copy(alpha = 0.35f),
                            0.65f to Color.Transparent
                        )
                    )
            )
            // Bottom fade (rail headers + card titles legibility)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.85f)
                        )
                    )
            )
            // Overall tint
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
            )
            // Top scrim (logo + search pill legibility)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.55f),
                            1f to Color.Transparent
                        )
                    )
            )

            // Transparent top bar (hamburger, logo, search pill)
            TopBarOverlay(
                onToggleNav = { showNav = !showNav },
                onSearch = { navController.navigate(Routes.SEARCH) },
                modifier = Modifier.align(Alignment.TopStart)
            )

            // Transparent side nav overlay
            if (showNav) {
                SideNavOverlay(
                    onHome = { heroIndex = 0 },
                    onMovies = { navController.navigate(Routes.catalog("/aflam", moviesLabel)) },
                    onSeries = { navController.navigate(Routes.catalog("/mosalsalat", seriesLabel)) },
                    onSearch = { navController.navigate(Routes.SEARCH) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }

            // Content: hero + rails in one vertical scroll
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val heroItemH = (maxHeight * 0.40f).coerceIn(160.dp, 400.dp)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = if (showNav) 150.dp else 24.dp,
                        top = 64.dp,
                        end = 24.dp,
                        bottom = 20.dp
                    )
                ) {
                    // Hero content (focusable; D-pad ←/→ switches the hero)
                    if (hero != null) {
                        item {
                            var heroActive by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(heroItemH)
                                    .focusable()
                                    .focusRequester(heroFocus)
                                    .onFocusChanged { heroActive = it.hasFocus }
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown && heroCount > 1) {
                                            when (keyEvent.key) {
                                                Key.DirectionLeft -> {
                                                    heroIndex =
                                                        (heroIndex - 1 + heroCount) % heroCount
                                                    true
                                                }
                                                Key.DirectionRight -> {
                                                    heroIndex = (heroIndex + 1) % heroCount
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else {
                                            false
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(bottom = 24.dp)
                                ) {
                                    Text(
                                        text = hero.title,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 36.sp
                                        ),
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    // Focus indicator (gold bar under the title)
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 8.dp)
                                            .height(4.dp)
                                            .width(if (heroActive) 64.dp else 0.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    if (hero.description.isNotEmpty()) {
                                        Text(
                                            text = hero.description,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 14.sp
                                            ),
                                            color = Color(0xFFD6D6D6),
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .padding(top = 10.dp)
                                                .widthIn(max = 600.dp)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.padding(top = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Solid gold "Watch now"
                                        Button(
                                            onClick = {
                                                navController.navigate(
                                                    Routes.servers(hero.url, hero.title, hero.posterUrl)
                                                )
                                            },
                                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                            colors = ButtonDefaults.colors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                                focusedContainerColor = MaterialTheme.colorScheme.primary,
                                                focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                                                pressedContainerColor = MaterialTheme.colorScheme.primary.copy(
                                                    alpha = 0.85f
                                                )
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                stringResource(R.string.watch_now),
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                        // Circular "+" — add to recently watched
                                        HeroCircleButton(
                                            onClick = {
                                                recent = RecentlyWatched.add(
                                                    context,
                                                    hero.title,
                                                    hero.url,
                                                    hero.posterUrl
                                                ).toItems()
                                            },
                                            icon = Icons.Filled.Add
                                        )
                                    }
                                    // Hero page dots
                                    if (heroCount > 1) {
                                        Row(
                                            modifier = Modifier.padding(top = 14.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            repeat(heroCount) { i ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (i == heroIndex) {
                                                                MaterialTheme.colorScheme.primary
                                                            } else {
                                                                Color.White.copy(alpha = 0.35f)
                                                            }
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Loading / error
                    if (featured == null && !error) {
                        item {
                            LoadingBox(Modifier.fillMaxWidth().height(heroItemH))
                        }
                    }
                    if (error && featured == null) {
                        item {
                            ErrorBox(
                                message = stringResource(R.string.error_generic),
                                onRetry = { loadKey++ },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(heroItemH)
                            )
                        }
                    }

                    // Rail 1: Popular (new movies)
                    val feat = featured
                    if (feat != null && feat.isNotEmpty()) {
                        item {
                            ContentRail(
                                title = stringResource(R.string.popular),
                                items = feat,
                                modifier = Modifier.padding(bottom = 20.dp),
                                onItem = { movieOnClick(it) }
                            )
                        }
                    }

                    // Rail 2: Recently watched
                    val rec = recent
                    if (rec != null && rec.isNotEmpty()) {
                        item {
                            ContentRail(
                                title = stringResource(R.string.recently_watched),
                                items = rec,
                                modifier = Modifier.padding(bottom = 20.dp),
                                onItem = { recentOnClick(it) }
                            )
                        }
                    }

                    // Rail 3: Movies (all)
                    val movs = allMovies
                    if (movs != null && movs.isNotEmpty()) {
                        item {
                            ContentRail(
                                title = moviesLabel,
                                items = movs,
                                modifier = Modifier.padding(bottom = 20.dp),
                                onItem = { movieOnClick(it) }
                            )
                        }
                    }

                    // Rail 4: Series (all)
                    val ser = allSeries
                    if (ser != null && ser.isNotEmpty()) {
                        item {
                            ContentRail(
                                title = seriesLabel,
                                items = ser,
                                modifier = Modifier.padding(bottom = 20.dp),
                                onItem = { seriesOnClick(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Circular icon button for the hero ("add to list").
 */
@Composable
private fun HeroCircleButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    val unit = remember { Unit }
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .focusable()
            .pointerInput(unit) {
                detectTapGestures(onTap = { onClick() })
            }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .size(24.dp)
        )
    }
}

/**
 * Recently watched prefs entries → VideoItems for the rails.
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

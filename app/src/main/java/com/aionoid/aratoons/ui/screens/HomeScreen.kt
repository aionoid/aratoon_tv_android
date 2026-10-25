package com.aionoid.aratoons.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import com.aionoid.aratoons.App
import com.aionoid.aratoons.R
import com.aionoid.aratoons.data.RecentlyWatched
import com.aionoid.aratoons.data.StardimaApi
import com.aionoid.aratoons.data.VideoItem
import com.aionoid.aratoons.ui.BlurredImage
import com.aionoid.aratoons.ui.ContentRail
import com.aionoid.aratoons.ui.ErrorBox
import com.aionoid.aratoons.ui.HeroDots
import com.aionoid.aratoons.ui.LoadingBox
import com.aionoid.aratoons.ui.Routes
import com.aionoid.aratoons.ui.SideNavOverlay
import com.aionoid.aratoons.ui.TopBarOverlay
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Home screen — a port of stardima_tv_flutter's home_screen.dart:
 *
 *  - Layer 0: the selected hero's poster, blurred, filling the whole
 *    screen (behind everything).
 *  - Layer 1: left column = the always-visible glass side nav (logo,
 *    Home / Movies / Series / Recently / Search + Settings pinned),
 *    right column = top bar (title + search field pill), the hero
 *    (0.42 of the screen height, clamped 230–320dp) and the rails
 *    (Recently / Popular / Series) in one vertical scroll.
 *
 * D-pad: ← / → on the hero switches the featured item (and the
 * full-screen background); UP from the hero goes to the search field;
 * DOWN lands on the first rail card. UP/DOWN between rails crosses the
 * rail boundary to the same card index of the neighbouring rail; LEFT
 * at the leftmost card of the top rail goes to the side nav.
 */
@Composable
fun HomeScreen(navController: NavHostController) {
    val app = App.instance
    val context = LocalContext.current

    var featured by remember { mutableStateOf<List<VideoItem>?>(null) }
    var recent by remember { mutableStateOf<List<VideoItem>?>(null) }
    var allSeries by remember { mutableStateOf<List<VideoItem>?>(null) }
    var error by remember { mutableStateOf(false) }
    var loadKey by remember { mutableStateOf(0) }
    var heroIndex by rememberSaveable { mutableStateOf(0) }

    val heroFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val navFocus = remember { FocusRequester() }
    val cardNodes = remember { mutableMapOf<Int, FocusRequester>() }
    val railStates = remember { mutableMapOf<Int, androidx.compose.foundation.lazy.LazyListState>() }
    val railColumnState = remember { androidx.compose.foundation.lazy.LazyListState() }

    LaunchedEffect(loadKey) {
        featured = null; recent = null; allSeries = null
        error = false; heroIndex = 0
        try {
            // Recently watched comes from prefs — show it while network loads
            recent = RecentlyWatched.load(context).toItems()
            // Network: "الرائج" (Popular) home-page carousel + first series page
            val (pop, seriesPage) = coroutineScope {
                val pJob = async { StardimaApi.popular(app.http) }
                val sJob = async { StardimaApi.catalog(app.http, "/mosalsalat", 1) }
                Pair(pJob.await(), sJob.await().first)
            }
            featured = pop
            allSeries = seriesPage
        } catch (e: Exception) {
            android.util.Log.e("HomeScreen", "load failed: $e", e)
            error = true
        }
    }

    // Leanback: start focus on the hero once data is in
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

    // Rails in the Flutter order: Recently (if any), Popular, Series.
    val rails = buildList {
        val rec = recent
        if (rec != null && rec.isNotEmpty()) add(stringResource(R.string.recently_watched) to rec)
        val feat = featured
        if (feat != null && feat.isNotEmpty()) add(stringResource(R.string.popular) to feat)
        val ser = allSeries
        if (ser != null && ser.isNotEmpty()) add(seriesLabel to ser)
    }
    val railHandlers = listOf(
        { it: VideoItem -> recentOnClick(it) },
        { it: VideoItem -> movieOnClick(it) },
        { it: VideoItem -> seriesOnClick(it) }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 0: full-screen blurred hero background
        if (hero?.posterUrl?.isNotEmpty() == true) {
            BlurredImage(
                model = hero.posterUrl,
                modifier = Modifier.fillMaxSize(),
                blurRadius = 26f,
                fullFrame = true,
                alpha = 0.6f
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

        // Layer 1: side nav + content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            SideNavOverlay(
                active = "home",
                onNavigate = { key ->
                    when (key) {
                        "home" -> heroIndex = 0
                        "movies" -> navController.navigate(Routes.catalog("/aflam", moviesLabel))
                        "series" -> navController.navigate(Routes.catalog("/mosalsalat", seriesLabel))
                        "recently" -> navController.navigate(Routes.RECENTLY)
                        "search" -> navController.navigate(Routes.SEARCH)
                        "settings" -> navController.navigate(Routes.SETTINGS)
                    }
                },
                navEntry = navFocus,
                contentEntry = heroFocus,
                modifier = Modifier.fillMaxHeight()
            )
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                TopBarOverlay(
                    title = stringResource(R.string.home),
                    onSearch = { q -> navController.navigate(Routes.search(q)) },
                    searchFocus = searchFocus,
                    navEntry = navFocus,
                    heroFocus = heroFocus
                )
                Spacer(Modifier.height(20.dp))

                // Hero (0.42 of the remaining height, clamped 230–320dp)
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val heroH = (maxHeight * 0.42f).coerceIn(230.dp, 320.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heroH)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.25f))
                            .focusable()
                            .focusRequester(heroFocus)
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when (keyEvent.key) {
                                    Key.DirectionLeft -> when {
                                        heroCount > 1 && heroIndex > 0 -> {
                                            heroIndex -= 1
                                            true
                                        }
                                        else -> {
                                            navFocus.requestFocus()
                                            true
                                        }
                                    }
                                    Key.DirectionRight -> when {
                                        heroCount > 1 && heroIndex < heroCount - 1 -> {
                                            heroIndex += 1
                                            true
                                        }
                                        else -> false
                                    }
                                    Key.DirectionUp -> {
                                        searchFocus.requestFocus()
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        // Land on the first card of the first rail
                                        val node = cardNodes[0]
                                        if (node != null) {
                                            node.requestFocus()
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    else -> false
                                }
                            }
                    ) {
                        // Hero poster (cover) + legibility gradients
                        if (hero?.posterUrl?.isNotEmpty() == true) {
                            BlurredImage(
                                model = hero.posterUrl,
                                modifier = Modifier.fillMaxSize(),
                                blurRadius = 14f,
                                fullFrame = true,
                                alpha = 0.9f
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            0f to Color.Black.copy(alpha = 0.75f),
                                            0.45f to Color.Black.copy(alpha = 0.35f),
                                            1f to Color.Transparent
                                        )
                                    )
                            )
                        }

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

                        // Hero content (bottom-left)
                        if (hero != null) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(24.dp)
                            ) {
                                Text(
                                    text = hero.title,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 32.sp
                                    ),
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (hero.description.isNotEmpty()) {
                                    Text(
                                        text = hero.description,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 14.sp
                                        ),
                                        color = Color(0xFFD6D6D6),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .padding(top = 10.dp)
                                            .widthIn(max = 600.dp)
                                    )
                                }
                                Row(
                                    modifier = Modifier.padding(top = 14.dp),
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
                                        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
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
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            stringResource(R.string.watch_now),
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
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
                                // Hero carousel dots
                                if (heroCount > 1) {
                                    HeroDots(
                                        count = heroCount,
                                        index = heroIndex,
                                        modifier = Modifier.padding(top = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))

                // Rails in one vertical scroll
                LazyColumn(
                    state = railColumnState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
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
                                heroFocus = heroFocus,
                                navEntry = navFocus,
                                cardNodes = cardNodes,
                                railStates = railStates,
                                railColumnState = railColumnState
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
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            // clickable() so D-pad CENTER activates it
            .clickable(interactionSource = null, indication = null) { onClick() }
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

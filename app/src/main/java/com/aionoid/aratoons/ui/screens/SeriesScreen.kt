package com.aionoid.aratoons.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.aionoid.aratoons.App
import com.aionoid.aratoons.R
import com.aionoid.aratoons.data.Episode
import com.aionoid.aratoons.data.SeasonInfo
import com.aionoid.aratoons.data.StardimaApi
import com.aionoid.aratoons.ui.ErrorBox
import com.aionoid.aratoons.ui.LoadingBox
import com.aionoid.aratoons.ui.Routes
import com.aionoid.aratoons.ui.systemBarsContentPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Series detail — two-step, YouTube channel-page style (reference:
 * ~/Downloads/episods lists.jpg). The series header stays fixed on the left;
 * the right side hosts a two-step list with a smooth fade + horizontal-slide
 * transition:
 *
 *  Step 1 — seasons: "Season N" rows (number badge + label + arrow).
 *  ENTER / tap selects a season.
 *  Step 2 — episodes: badge + title + "Season N" rows. ENTER / tap plays
 *  (server screen). LEFT goes back to the season list. BACK pops the stack.
 *
 *  D-pad: UP/DOWN move between rows (clamped at the edges); CENTER (ENTER)
 *  triggers the action; LEFT/RIGHT are consumed in the seasons step (vertical
 *  only); BACK always bubbles to the nav host.
 */
@Composable
fun SeriesScreen(slug: String, navController: NavHostController) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    val gold = MaterialTheme.colorScheme.primary

    var info by remember { mutableStateOf<SeasonInfo?>(null) }
    var infoError by remember { mutableStateOf(false) }
    var loadKey by remember { mutableIntStateOf(0) }

    // null = season list; non-null = episode list for that season id.
    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    var episodes by remember { mutableStateOf<List<Episode>?>(null) }
    var epLoading by remember { mutableStateOf(false) }
    var epError by remember { mutableStateOf(false) }
    var epLoadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(slug, loadKey) {
        infoError = false
        try {
            info = StardimaApi.seasons(app.http, slug)
        } catch (e: Exception) {
            android.util.Log.e("SeriesScreen", "seasons failed: $e", e)
            infoError = true
        }
    }

    LaunchedEffect(selectedSeason, epLoadKey) {
        selectedSeason?.let { seasonId ->
            episodes = null
            epLoading = true
            epError = false
            try {
                episodes = StardimaApi.episodes(app.http, seasonId)
            } catch (e: Exception) {
                android.util.Log.e("SeriesScreen", "episodes failed: $e", e)
                epError = true
            }
            epLoading = false
        }
    }

    val infoData = info
    if (infoData == null) {
        LoadingBox(Modifier.fillMaxSize())
        return
    }
    if (infoError || infoData.seasons.isEmpty()) {
        ErrorBox(
            message = stringResource(
                if (infoError) R.string.error_network else R.string.no_results
            ),
            onRetry = { loadKey++ },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    val showingEpisodes = selectedSeason != null
    val seasonCount = infoData.seasons.size
    val selectedSeasonPos = selectedSeason?.let { infoData.seasons.indexOf(it) + 1 } ?: 0

    // Maps holding only currently-composed FocusRequesters (FocusMap pattern):
    // requestFocus() on a disposed requester throws IllegalStateException.
    val seasonFocusMap = remember { mutableMapOf<Int, FocusRequester>() }
    val epFocusMap = remember { mutableMapOf<Int, FocusRequester>() }
    val emptyEpFocus = remember { FocusRequester() }

    val seasonListState = rememberLazyListState()
    val epListState = rememberLazyListState()

    var initialFocusDone by remember { mutableStateOf(false) }

    // Move focus to row `index` of a list: scroll into view, then focus it
    // (retrying once in case the row is still composing).
    fun focusRowIndex(
        listState: LazyListState,
        focusMap: MutableMap<Int, FocusRequester>,
        index: Int
    ) {
        scope.launch {
            listState.scrollToItem(index)
            delay(40L)
            focusMap[index]?.let { fr ->
                try { fr.requestFocus() } catch (_: IllegalStateException) {}
            } ?: run {
                delay(120L)
                focusMap[index]?.let { fr ->
                    try { fr.requestFocus() } catch (_: IllegalStateException) {}
                }
            }
        }
    }

    // First focus: the first season row (fresh entry only).
    LaunchedEffect(infoData) {
        if (initialFocusDone || selectedSeason != null) return@LaunchedEffect
        delay(24L)
        focusRowIndex(seasonListState, seasonFocusMap, 0)
        initialFocusDone = true
    }

    // After (re)loading episodes, focus the first episode (or the empty state)
    // once the transition has finished.
    LaunchedEffect(episodes, selectedSeason) {
        val eps = episodes
        if (selectedSeason == null || eps == null) return@LaunchedEffect
        delay(340L)
        if (eps.isEmpty()) {
            try { emptyEpFocus.requestFocus() } catch (_: IllegalStateException) {}
        } else {
            focusRowIndex(epListState, epFocusMap, 0)
        }
    }

    fun selectSeason(season: Int) {
        selectedSeason = season
    }

    fun backToSeasons() {
        val seasonId = selectedSeason ?: return
        selectedSeason = null
        scope.launch {
            delay(340L) // after the transition
            focusRowIndex(seasonListState, seasonFocusMap, infoData.seasons.indexOf(seasonId))
        }
    }

    fun playEpisode(ep: Episode) {
        val seasonPos = infoData.seasons.indexOf(selectedSeason ?: 0) + 1
        navController.navigate(
            Routes.servers(
                "/play/${ep.id}",
                ep.title.ifEmpty { "${infoData.title} — S${seasonPos}E${ep.episodeNumber}" },
                infoData.poster
            )
        )
    }

    // BACK from the episode list returns to the season list (same as LEFT);
    // from the season list it bubbles to the nav host to pop the screen.
    BackHandler(enabled = selectedSeason != null) { backToSeasons() }

    val barInsets = systemBarsContentPadding()
    Box(
        modifier = Modifier
            .padding(barInsets)
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Left: persistent series header (non-focusable chrome) ──
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(190.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF2A2D31))
                ) {
                    if (infoData.poster.isNotEmpty()) {
                        AsyncImage(
                            model = StardimaApi.upscalePoster(infoData.poster, 780),
                            contentDescription = infoData.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = infoData.title.ifEmpty { slug },
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (infoData.description.isNotEmpty()) {
                        Text(
                            text = infoData.description,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "$seasonCount seasons",
                        style = MaterialTheme.typography.labelMedium,
                        color = gold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // ── Right: two-step list ──
            Column(
                modifier = Modifier.weight(2f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (showingEpisodes) "Season $selectedSeasonPos" else "Seasons",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(Modifier.weight(1f))
                    val rightText = if (showingEpisodes) {
                        episodes?.size?.takeIf { it > 0 }?.let { "$it episodes" }
                    } else {
                        "$seasonCount seasons"
                    }
                    if (rightText != null) {
                        Text(
                            text = rightText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    // Step 1 — seasons
                    AnimatedVisibility(
                        visible = !showingEpisodes,
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(300)),
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        val n = seasonCount
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = seasonListState,
                            contentPadding = PaddingValues(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(infoData.seasons, key = { _, season -> season }) { index, season ->
                                SeasonRow(
                                    index = index,
                                    badge = index + 1,
                                    label = "Season ${index + 1}",
                                    focusMap = seasonFocusMap,
                                    onClick = { selectSeason(season) },
                                    onKey = { event ->
                                        when (event.key) {
                                            Key.DirectionUp -> if (index > 0) {
                                                focusRowIndex(seasonListState, seasonFocusMap, index - 1); true
                                            } else true
                                            Key.DirectionDown -> if (index < n - 1) {
                                                focusRowIndex(seasonListState, seasonFocusMap, index + 1); true
                                            } else true
                                            Key.DirectionLeft, Key.DirectionRight -> true // vertical only
                                            else -> false
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Step 2 — episodes
                    AnimatedVisibility(
                        visible = showingEpisodes,
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(300)),
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        val eps = episodes
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = epListState,
                            contentPadding = PaddingValues(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (epError && eps == null) {
                                item {
                                    ErrorBox(
                                        message = stringResource(R.string.error_generic),
                                        onRetry = { epLoadKey++ },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp)
                                    )
                                }
                            } else if (epLoading || eps == null) {
                                item {
                                    LoadingBox(Modifier.fillMaxWidth().padding(vertical = 40.dp))
                                }
                            } else if (eps.isEmpty()) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(48.dp)
                                            .focusRequester(emptyEpFocus)
                                            .clickable(interactionSource = null, indication = null) {
                                                epLoadKey++
                                            },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.no_results),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Press CENTER to retry",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = gold
                                        )
                                    }
                                }
                            } else {
                                val n = eps.size
                                itemsIndexed(eps, key = { _, ep -> ep.id }) { index, ep ->
                                    EpisodeRow(
                                        index = index,
                                        episode = ep,
                                        seasonPos = selectedSeasonPos,
                                        focusMap = epFocusMap,
                                        onClick = { playEpisode(ep) },
                                        onKey = { event ->
                                            when (event.key) {
                                                Key.DirectionUp -> if (index > 0) {
                                                    focusRowIndex(epListState, epFocusMap, index - 1); true
                                                } else true
                                                Key.DirectionDown -> if (index < n - 1) {
                                                    focusRowIndex(epListState, epFocusMap, index + 1); true
                                                } else true
                                                Key.DirectionLeft -> { backToSeasons(); true }
                                                Key.DirectionRight -> true
                                                else -> false
                                            }
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
}

// ── Row focus chrome (PosterCard language: glow ring, border, 1.02x zoom) ──

@Composable
private fun rowChrome(
    externalFocusRequester: FocusRequester,
    onKey: (KeyEvent) -> Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
): Modifier {
    var active by remember { mutableStateOf(false) }
    val gold = MaterialTheme.colorScheme.primary
    return modifier
        .fillMaxWidth()
        .background(
            color = if (active) gold.copy(alpha = 0.35f) else Color(0xFF2A2D31),
            shape = RoundedCornerShape(12.dp)
        )
        .border(
            width = if (active) 3.dp else 1.dp,
            color = if (active) gold else Color.White.copy(alpha = 0.18f),
            shape = RoundedCornerShape(12.dp)
        )
        .clip(RoundedCornerShape(12.dp))
        .padding(horizontal = 14.dp, vertical = 10.dp)
        .onFocusChanged { active = it.hasFocus }
        .focusRequester(externalFocusRequester)
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            onKey(event)
        }
        .clickable(interactionSource = null, indication = null) { onClick() }
}

@Composable
private fun SeasonRow(
    index: Int,
    badge: Int,
    label: String,
    focusMap: MutableMap<Int, FocusRequester>,
    onClick: () -> Unit,
    onKey: (KeyEvent) -> Boolean,
    modifier: Modifier = Modifier
) {
    val fr = remember { FocusRequester() }
    DisposableEffect(fr) {
        focusMap[index] = fr
        onDispose { focusMap.remove(index) }
    }
    Row(
        modifier = rowChrome(fr, onKey, onClick, modifier),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NumberBadge(badge)
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Filled.ArrowForward,
            contentDescription = "Open season",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun EpisodeRow(
    index: Int,
    episode: Episode,
    seasonPos: Int,
    focusMap: MutableMap<Int, FocusRequester>,
    onClick: () -> Unit,
    onKey: (KeyEvent) -> Boolean,
    modifier: Modifier = Modifier
) {
    val fr = remember { FocusRequester() }
    DisposableEffect(fr) {
        focusMap[index] = fr
        onDispose { focusMap.remove(index) }
    }
    Row(
        modifier = rowChrome(fr, onKey, onClick, modifier),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NumberBadge(episode.episodeNumber.coerceAtLeast(1))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Season $seasonPos",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NumberBadge(number: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

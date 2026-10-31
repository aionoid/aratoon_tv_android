package com.aionoid.aratoons.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.aionoid.aratoons.R
import com.aionoid.aratoons.data.Favorites
import com.aionoid.aratoons.data.StardimaApi
import com.aionoid.aratoons.ui.LoadingBox
import com.aionoid.aratoons.ui.Routes
import com.aionoid.aratoons.ui.systemBarsContentPadding

/**
 * Favourites screen (the 7th tab in the top bar). Shows every favourited
 * item as a poster card in a vertical grid (same grid layout as Movies /
 * Series). ENTER / tap opens the item; when a card is focused, a
 * "remove from favourites" chip appears (DOWN key).
 */
@Composable
fun FavoritesScreen(
    navController: NavHostController,
    focusEntry: FocusRequester? = null
) {
    val gold = MaterialTheme.colorScheme.primary
    val context = LocalContext.current

    var favorites by remember { mutableStateOf<List<Favorites.Entry>>(emptyList()) }
    var favoritesLoading by remember { mutableStateOf(true) }
    val firstFocus = focusEntry ?: remember { FocusRequester() }

    LaunchedEffect(context) {
        favoritesLoading = true
        favorites = Favorites.load(context)
        favoritesLoading = false
    }

    val list = favorites

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 168.dp),
        contentPadding = systemBarsContentPadding(
            start = 24.dp,
            top = 16.dp,
            end = 24.dp,
            bottom = 24.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Header: title + count badge
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.favorites),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                if (list.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(gold),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            list.size.toString(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.Black.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        if (favoritesLoading && list.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingBox(Modifier.size(48.dp))
                }
            }
        } else if (list.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.favorites_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(list, key = { it.id }) { entry ->
                FavoritesCard(
                    entry = entry,
                    navController = navController,
                    onRemove = {
                        favorites = Favorites.remove(context, entry)
                    },
                    focusRequester = if (firstFocus != null && entry == list.firstOrNull()) firstFocus else null
                )
            }
        }
    }
}

/**
 * One favourite card in the grid: 168dp-wide poster (252dp tall) with
 * title + type badge below, plus a hidden "remove" chip that appears
 * when the card gains focus.
 */
@Composable
private fun FavoritesCard(
    entry: Favorites.Entry,
    navController: NavHostController,
    onRemove: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    val gold = MaterialTheme.colorScheme.primary
    var focused by remember { mutableStateOf(false) }
    val isSeries = entry.type == "series"

    fun openEntry() {
        when (entry.type) {
            "movie" -> navController.navigate(
                Routes.movie(entry.url, entry.title, entry.poster, entry.year, entry.desc, entry.quality)
            )
            "series" -> navController.navigate(
                Routes.series(entry.url)
            )
        }
    }

    Column(
        modifier = Modifier
            .width(168.dp)
            .onFocusChanged { focused = it.hasFocus }
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .focusable()
            .clickable(interactionSource = null, indication = null) { openEntry() }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Enter -> {
                        openEntry()
                        true
                    }
                    Key.DirectionDown -> {
                        onRemove()
                        true
                    }
                    else -> false
                }
            }
    ) {
        Box(
            modifier = Modifier
                .width(168.dp)
                .height(252.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2A2D31))
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) gold else Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            if (entry.poster.isNotEmpty()) {
                AsyncImage(
                    model = StardimaApi.upscalePoster(entry.poster, 780),
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.title.ifEmpty { "Untitled" },
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            ),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(50))
                .background(gold.copy(alpha = 0.2f))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(
                text = if (isSeries) "Series" else "Movie",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = gold
            )
        }

        AnimatedVisibility(visible = focused) {
            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2A2D31))
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.25f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.unfavorite),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.unfavorite),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

package com.aionoid.aratoons.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.aionoid.aratoons.R
import com.aionoid.aratoons.ui.screens.CatalogScreen
import com.aionoid.aratoons.ui.screens.HomeScreen
import com.aionoid.aratoons.ui.screens.RecentlyWatchedScreen
import com.aionoid.aratoons.ui.screens.SearchScreen
import com.aionoid.aratoons.ui.screens.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * App shell: a top tab bar (Android TV "Tabs" component) and the selected
 * tab's content below it. Replaces the old left SideNavOverlay + TopBarOverlay
 * chrome: the six main destinations are tabs at the top, item-level
 * navigation (servers, player, series) is pushed on top of the shell.
 *
 * Focus model (deterministic, TV-friendly):
 *   Tab-bar level : LEFT/RIGHT switch tabs (content follows); ENTER/DOWN
 *                   (OK) enter content; UP is consumed (nothing is above).
 *   Content level : UP returns to the tab bar; DOWN/LEFT/RIGHT/ENTER are
 *                   handled by the content screen itself.
 *
 * Focus is owned by the shell: each tab and each content area has its own
 * FocusRequester. Screens accept [focusEntry] and do NOT auto-request focus
 * (the shell requests it at the right moment), so switching tabs never
 * steals focus from the tab bar.
 */
@Composable
fun AraToonsShell(navController: NavHostController) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    val tabs = remember {
        listOf(
            TabSpec(R.string.home, Icons.Filled.Home),
            TabSpec(R.string.movies, Icons.Filled.Movie),
            TabSpec(R.string.series, Icons.Filled.AutoAwesome),
            TabSpec(R.string.recently_watched, Icons.Filled.History),
            TabSpec(R.string.search, Icons.Filled.Search),
            TabSpec(R.string.settings, Icons.Filled.Settings),
        )
    }
    val tabFocusRequests = remember { tabs.map { FocusRequester() } }
    val contentFocusRequests = remember { tabs.map { FocusRequester() } }

    fun selectTab(tab: Int) {
        if (tab == selectedTab) return
        selectedTab = tab
        scope.launch {
            delay(16L)
            tabFocusRequests[tab].requestFocus()
        }
    }

    fun enterContent(tab: Int) {
        scope.launch {
            delay(50L)
            try {
                contentFocusRequests[tab].requestFocus()
            } catch (e: IllegalStateException) {
                // Content not composed yet; the screen will anchor focus.
            }
        }
    }

    // Initial focus: the first tab.
    LaunchedEffect(Unit) {
        delay(16L)
        tabFocusRequests[0].requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabBar(
            tabs = tabs,
            selected = selectedTab,
            tabFocusRequests = tabFocusRequests,
            selectTab = { tab -> selectTab(tab) },
            enterContent = { tab -> enterContent(tab) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                )
                .height(52.dp)
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> HomeScreen(
                    navController,
                    previewFocus = contentFocusRequests[0],
                    tabBarEntry = tabFocusRequests[0]
                )
                1 -> CatalogScreen(
                    path = "/aflam",
                    title = stringResource(R.string.movies),
                    navController = navController,
                    focusEntry = contentFocusRequests[1]
                )
                2 -> CatalogScreen(
                    path = "/mosalsalat",
                    title = stringResource(R.string.series),
                    navController = navController,
                    focusEntry = contentFocusRequests[2]
                )
                3 -> RecentlyWatchedScreen(navController, focusEntry = contentFocusRequests[3])
                4 -> SearchScreen(navController, initialQuery = "", focusEntry = contentFocusRequests[4])
                5 -> SettingsScreen()
            }
        }
    }
}

// A tab descriptor. Kept in-file but as a data class (not private) so it can
// be exposed as a parameter type of the public [TabBar].
data class TabSpec(
    val labelRes: Int,
    val icon: ImageVector
)

/**
 * Tab bar: wordmark + horizontal scrollable row of tabs with a gold PILL
 * indicator for the selected tab. Each tab is focusable; D-pad moves between
 * tabs (LEFT/RIGHT), ENTER/DOWN enter the content, UP is consumed.
 */
@Composable
private fun TabBar(
    tabs: List<TabSpec>,
    selected: Int,
    tabFocusRequests: List<FocusRequester>,
    selectTab: (Int) -> Unit,
    enterContent: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val gold = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App wordmark (non-focusable chrome)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Ara",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = Color.White
            )
            Text(
                "Toons",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = gold
            )
            Box(
                modifier = Modifier
                    .padding(start = 6.dp, top = 2.dp, bottom = 2.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(gold.copy(alpha = 0.2f), RoundedCornerShape(5.dp))
                    .border(1.dp, gold.copy(alpha = 0.4f), RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.tv_app),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    ),
                    color = gold
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        // Tabs: a regular Row (not LazyRow — 6 fixed items) centred in the
        // remaining space after the wordmark, per the Android TV "Tabs"
        // pattern.
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                TabItem(
                    label = stringResource(tab.labelRes),
                    icon = tab.icon,
                    selected = selected == index,
                    focusRequester = tabFocusRequests[index],
                    onKey = { event ->
                        val keyDown = event.type == KeyEventType.KeyDown
                        if (!keyDown) {
                            false
                        } else when (event.key) {
                            Key.DirectionLeft -> {
                                if (index > 0) selectTab(index - 1)
                                true
                            }
                            Key.DirectionRight -> {
                                if (index < tabs.size - 1) selectTab(index + 1)
                                true
                            }
                            Key.DirectionDown -> { enterContent(index); true }
                            Key.Enter -> { enterContent(index); true }
                            Key.DirectionUp -> true
                            else -> false
                        }
                    },
                    onClick = { selectTab(index) }
                )
            }
        }
    }
}

/**
 * One tab: gold PILL when selected; subtle scale + gold border when
 * focused-but-unselected; dimmed otherwise.
 */
@Composable
private fun TabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    focusRequester: FocusRequester,
    onKey: (KeyEvent) -> Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gold = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && !selected) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.72f)
    )
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .height(40.dp)
            // No fixed min width: unselected tabs are icon-only, the selected
            // tab expands to icon + text.
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .scale(scale)
            .clip(shape)
            .background(if (selected) gold else Color.Transparent, shape)
            .border(
                width = if (selected) 0.dp else if (focused) 1.5.dp else 0.dp,
                color = gold,
                shape = shape
            )
            // onFocusChanged before the focus targets (see PosterCard)
            .onFocusChanged { focused = it.hasFocus }
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown) onKey(e) else false }
            .clickable(interactionSource = null, indication = null) { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when {
                selected -> onPrimary
                focused -> Color.White
                else -> Color.White.copy(alpha = 0.75f)
            },
            modifier = Modifier.size(18.dp)
        )
        // Only the selected tab expands to icon + text; others stay icon-only.
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = onPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

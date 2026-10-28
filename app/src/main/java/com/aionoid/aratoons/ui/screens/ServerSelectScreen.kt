package com.aionoid.aratoons.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aionoid.aratoons.App
import com.aionoid.aratoons.R
import com.aionoid.aratoons.data.ServerInfo
import kotlinx.coroutines.delay

/**
 * Server selection screen — a port of stardima_tv_flutter's
 * server_select_screen.dart (see docs/screenshots/servers.png):
 *
 *  - Left: a prominent poster (230dp, 2:3, rounded 16).
 *  - Right: the title (30sp extra-bold, 2 lines), the hint, and the
 *    servers as compact wrapping pills (status dot + name) — not tall
 *    full-width rows.
 *
 * Loads the servers advertised by the hyperwatching embed for the video
 * and lets the user pick one — servers differ in speed, availability,
 * languages and quality. Videos with a single server skip the list and
 * play directly.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerSelectScreen(
    playUrl: String,
    title: String,
    poster: String,
    onBack: () -> Unit,
    onServerSelected: (String?) -> Unit
) {
    val app = App.instance
    var servers by remember { mutableStateOf<List<ServerInfo>?>(null) }
    var loadKey by remember { mutableStateOf(0) }
    var autoNavigated by remember { mutableStateOf(false) }
    val listFocus = remember { FocusRequester() }

    LaunchedEffect(loadKey) {
        servers = null
        autoNavigated = false
        val list = runCatching { app.resolver.getServers(playUrl) }.getOrDefault(emptyList())
        servers = list
        if (list.size == 1 && !autoNavigated) {
            autoNavigated = true
            // Brief pause so the list screen can paint before leaving it.
            delay(300)
            onServerSelected(list[0].name)
        }
    }

    LaunchedEffect(servers) {
        servers?.takeIf { it.isNotEmpty() }?.let { listFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        when {
            servers == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            servers!!.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    stringResource(R.string.error_no_servers),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Button(onClick = { loadKey++ }) {
                        Text(stringResource(R.string.retry))
                    }
                    Button(onClick = { onServerSelected(null) }) {
                        Text(stringResource(R.string.play_auto))
                    }
                }
            }

            else -> Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Left: prominent poster (Flutter: 230px, 2:3, radius 16)
                Box(
                    modifier = Modifier
                        .width(230.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF2A2D31))
                ) {
                    if (poster.isNotEmpty()) {
                        AsyncImage(
                            model = poster,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                // Right: title + hint + wrapping server pills
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.select_server_hint),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            servers!!.forEachIndexed { index, server ->
                                ServerPill(
                                    server = server,
                                    focusRequester = if (index == 0) listFocus else null,
                                    onClick = { onServerSelected(server.name) }
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
 * One compact, selectable server pill (Flutter _ServerPill): 48dp-tall
 * stadium shape, translucent white fill, thin light border, a status dot
 * (green = ready, orange = processing) and the server name. Focus zooms
 * 1.05 (Flutter focusScale) and highlights the border gold.
 */
@Composable
private fun ServerPill(
    server: ServerInfo,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.72f)
    )
    val processing = server.status.lowercase() == "processing"
    val statusColor = if (processing) Color(0xFFFFB74D) else Color(0xFF69F0AE)
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .height(48.dp)
            .scale(scale)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.07f), shape)
            .border(
                width = 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.18f),
                shape = shape
            )
            // onFocusChanged before the focusable node — it only aggregates
            // focus targets that follow it in the chain (see PosterCard)
            .onFocusChanged { focused = it.hasFocus }
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .focusable()
            // Handles D-pad ENTER and touch taps alike (like the side nav)
            .clickable(interactionSource = null, indication = null) { onClick() }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Text(
            server.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

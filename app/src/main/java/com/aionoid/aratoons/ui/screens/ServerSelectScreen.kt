package com.aionoid.aratoons.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import coil.compose.AsyncImage
import com.aionoid.aratoons.App
import com.aionoid.aratoons.R
import com.aionoid.aratoons.data.ServerInfo

/**
 * Server selection screen, shown between the catalog and the player.
 *
 * Loads the servers advertised by the hyperwatching embed for the video and
 * lets the user pick one — servers differ in speed, availability, languages
 * and quality. Videos with a single server skip the list and play directly.
 */
@Composable
fun ServerSelectScreen(
    playUrl: String,
    title: String,
    poster: String,
    onBack: () -> Unit,
    onServerSelected: (String?) -> Unit
) {
    val app = App.instance
    val context = LocalContext.current
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
            kotlinx.coroutines.delay(300)
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
                // Left: poster + title (140dp keeps the server list wide enough
                // on phone screens; TVs get a lot more room for the list)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(140.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp))
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
                    Spacer(Modifier.height(12.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // Right: hint + server list
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.select_server),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        stringResource(R.string.select_server_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    servers!!.forEachIndexed { index, server ->
                        ServerRow(
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

/** One selectable server row: name + status badge. */
@Composable
private fun ServerRow(
    server: ServerInfo,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    val statusText = when (server.status.lowercase()) {
        "processing" -> stringResource(R.string.server_processing)
        else -> stringResource(R.string.server_ready)
    }
    val statusColor = when (server.status.lowercase()) {
        "processing" -> Color(0xFFFFB74D)
        else -> Color(0xFF69F0AE)
    }
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .pointerInput(Unit) {
                // tv-material 1.0.1's tvClickable only handles D-pad enter — add touch
                detectTapGestures(onTap = { onClick() })
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    server.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.server_id, server.id),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(statusColor.copy(alpha = 0.18f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor
                )
            }
        }
    }
}

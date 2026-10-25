package com.aionoid.aratoons.ui.screens

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.aionoid.aratoons.App
import com.aionoid.aratoons.R
import com.aionoid.aratoons.ui.ActionPill
import com.aionoid.aratoons.data.RecentlyWatched
import com.aionoid.aratoons.data.ServerInfo
import com.aionoid.aratoons.data.StreamResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Video player: resolves the stream, plays it with ExoPlayer and provides
 * M3 controls (play/pause, seek, audio track selection, server fallback).
 */
@Composable
fun PlayerScreen(url: String, title: String, poster: String, server: String? = null) {
    val app = App.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pstate by remember { mutableStateOf(0) } // 0=loading 1=playing 2=error
    var errorMsg by remember { mutableStateOf("") }
    var stream by remember { mutableStateOf<StreamResult?>(null) }
    var servers by remember { mutableStateOf<List<ServerInfo>?>(null) }
    var showServerDialog by remember { mutableStateOf(false) }
    // Pre-selected server from the server-selection screen (null = auto).
    var retryServer by remember { mutableStateOf<String?>(server) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var audioTracks by remember { mutableStateOf<List<String>?>(null) }
    var selectedAudio by remember { mutableStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var loadKey by remember { mutableStateOf(0) }

    val player = remember {
        ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .build()
    }
    val focusRequester = remember { FocusRequester() }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    var recentAdded by remember { mutableStateOf(false) }

    fun refreshAudioTracks() {
        val groups = player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
        audioTracks = groups.map { g ->
            val t = g.getTrackFormat(0)
            t.label?.takeIf { it.isNotEmpty() }
                ?: t.language?.takeIf { it.isNotEmpty() }
                ?: "Audio"
        }
    }

    fun selectAudio(index: Int) {
        val groups = player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
        if (index < 0 || index >= groups.size) return
        selectedAudio = index
        val params = player.trackSelectionParameters.buildUpon()
            .clearOverrides()
            .addOverride(TrackSelectionOverride(groups[index].mediaTrackGroup, 0))
            .build()
        player.setTrackSelectionParameters(params)
    }

    fun bumpControls() {
        controlsVisible = true
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(5000)
            controlsVisible = false
        }
    }

    // Player listener
    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        buffering = false
                        refreshAudioTracks()
                        // Track selection diagnostics (audio-only playback hunt)
                        val sb = StringBuilder()
                        for (g in player.currentTracks.groups) {
                            sb.append("[type=").append(g.type)
                                .append(" selected=").append(g.isSelected())
                                .append(" tracks=").append(g.length)
                                .append("]")
                        }
                        android.util.Log.d("PlayerScreen", "READY: $sb")
                    }
                    Player.STATE_BUFFERING -> buffering = true
                    Player.STATE_ENDED -> { isPlaying = false; ended = true }
                    else -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                pstate = 2
                errorMsg = error.errorCodeName ?: "Playback error"
            }
        }
        player.addListener(listener)
    }

    // Resolve stream + prepare player
    LaunchedEffect(loadKey, retryServer) {
        pstate = 0
        stream = null
        ended = false
        controlsVisible = true
        try {
            val result = app.resolver.resolve(url, retryServer)
            if (result == null) {
                pstate = 2
                errorMsg = context.getString(R.string.error_no_stream)
                servers = app.resolver.getServers(url)
                if (servers.isNullOrEmpty()) showServerDialog = false
                return@LaunchedEffect
            }
            stream = result
            val httpFactory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(result.headers)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(30000)
            val mediaItem = MediaItem.Builder()
                .setUri(result.url)
                .build()
            val mediaSource = if (result.isHls) {
                HlsMediaSource.Factory(httpFactory).createMediaSource(mediaItem)
            } else {
                ProgressiveMediaSource.Factory(httpFactory).createMediaSource(mediaItem)
            }
            player.setMediaSource(mediaSource)
            player.prepare()
            player.play()
            pstate = 1
            if (!recentAdded) {
                RecentlyWatched.add(app, title, url, poster)
                recentAdded = true
            }
        } catch (e: Exception) {
            pstate = 2
            errorMsg = e.message ?: context.getString(R.string.error_generic)
        }
    }

    // Progress polling
    LaunchedEffect(pstate) {
        if (pstate != 1) return@LaunchedEffect
        while (true) {
            delay(500)
            positionMs = player.currentPosition
            durationMs = player.duration.coerceAtLeast(0L)
            isPlaying = player.playWhenReady && player.playbackState == Player.STATE_READY
        }
    }

    DisposableEffect(player) {
        onDispose {
            hideJob?.cancel()
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action != KeyEvent.ACTION_DOWN || pstate != 1) return@onKeyEvent false
                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (buffering) return@onKeyEvent true
                        if (isPlaying) player.pause() else player.play()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (durationMs > 0) {
                            player.seekTo(minOf(player.currentPosition + 10_000, durationMs))
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        player.seekTo(maxOf(0L, player.currentPosition - 10_000))
                        true
                    }
                    // Flutter player: UP shows the chrome, DOWN hides it
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        bumpControls()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        hideJob?.cancel()
                        controlsVisible = false
                        true
                    }
                    else -> false
                }
            }
    ) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        AndroidView(
            factory = { ctx ->
                // Inflated from XML so app:surface_type="texture_view" applies
                // (no public setter in media3 1.4.1). TextureView composes inside
                // the app window; SurfaceView's hole-punch rendering goes black
                // on Waydroid (containerized Android) even though the video
                // decodes fine.
                (android.view.LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as androidx.media3.ui.PlayerView).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading / error overlays
        if (pstate == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.resolving_stream),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }
        if (pstate == 2) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        errorMsg,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = {
                            retryServer = null
                            loadKey++
                        }) {
                            Text(stringResource(R.string.retry))
                        }
                        if (!servers.isNullOrEmpty()) {
                            TextButton(onClick = { showServerDialog = true }) {
                                Text(stringResource(R.string.select_server))
                            }
                        }
                    }
                }
            }
        }

        // Ended overlay (Flutter player): "Ended" + gold Replay pill
        if (pstate == 1 && ended) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.ended),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Spacer(Modifier.height(16.dp))
                    ActionPill(
                        label = stringResource(R.string.replay),
                        gold = true,
                        onClick = {
                            player.seekTo(0)
                            player.play()
                            ended = false
                        }
                    )
                }
            }
        }

        // Controls overlay
        if (pstate == 1 && controlsVisible) {
            val audioList = audioTracks
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BrushGradientTop())
            ) {
                // Top bar: title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!audioList.isNullOrEmpty() && audioList.size > 1) {
                        IconButton(onClick = { showAudioDialog = true }) {
                            Icon(
                                Icons.Filled.Audiotrack,
                                contentDescription = stringResource(R.string.audio),
                                tint = Color.White
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                // Bottom bar: seek + controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatTime(positionMs),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                        Spacer(Modifier.width(12.dp))
                        Slider(
                            value = if (durationMs > 0) (positionMs.toFloat() / durationMs) else 0f,
                            onValueChange = { frac ->
                                if (durationMs > 0) {
                                    player.seekTo((frac * durationMs).toLong())
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            formatTime(durationMs),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (buffering) return@IconButton
                                if (isPlaying) {
                                    player.pause()
                                } else {
                                    player.play()
                                }
                            }
                        ) {
                            Icon(
                                if (isPlaying && !buffering) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.play),
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                    // Flutter player bottom info row: title + server + audio pills
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        val serverName = retryServer
                        if (!serverName.isNullOrBlank()) {
                            ActionPill(label = serverName, small = true) {}
                        }
                        if (!audioList.isNullOrEmpty() && audioList.size > 1) {
                            ActionPill(
                                label = audioList[selectedAudio.coerceIn(0, audioList.size - 1)],
                                small = true,
                                onClick = { showAudioDialog = true }
                            )
                        }
                    }
                }
            }
        }
    }

    // Server selection dialog
    val serverList = servers
    if (showServerDialog && !serverList.isNullOrEmpty()) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text(stringResource(R.string.select_server)) },
            text = {
                Column {
                    serverList.forEach { server ->
                        TextButton(
                            onClick = {
                                showServerDialog = false
                                retryServer = server.name
                                loadKey++
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "${server.name} (${server.status.ifEmpty { "ok" }})",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Audio selection dialog
    val audioList = audioTracks
    if (showAudioDialog && !audioList.isNullOrEmpty()) {
        AlertDialog(
            onDismissRequest = { showAudioDialog = false },
            title = { Text(stringResource(R.string.audio)) },
            text = {
                Column {
                    audioList.forEachIndexed { index, name ->
                        TextButton(
                            onClick = {
                                selectAudio(index)
                                showAudioDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                name + if (index == selectedAudio) "  ✓" else "",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

/** Simple vertical gradient for the controls overlay. */
@Composable
private fun BrushGradientTop(): androidx.compose.ui.graphics.Brush =
    androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(
            Color.Black.copy(alpha = 0.7f),
            Color.Transparent,
            Color.Transparent,
            Color.Black.copy(alpha = 0.7f)
        )
    )

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}

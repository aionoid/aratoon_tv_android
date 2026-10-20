package com.aionoid.aratoons.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as GCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import coil.Coil
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aionoid.aratoons.R
import com.aionoid.aratoons.data.VideoItem
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Edge-to-edge content padding: status bar inset on top, navigation bar
 * inset on the bottom, plus extra margins. Pass to a scrollable's
 * contentPadding so content can scroll behind the system bars.
 */
@Composable
fun systemBarsContentPadding(
    start: Dp = 0.dp,
    top: Dp = 0.dp,
    end: Dp = 0.dp,
    bottom: Dp = 0.dp
): PaddingValues {
    val statusBars = WindowInsets.statusBars.asPaddingValues()
    val navBars = WindowInsets.navigationBars.asPaddingValues()
    return PaddingValues(
        start = start,
        top = top + statusBars.calculateTopPadding(),
        end = end,
        bottom = bottom + navBars.calculateBottomPadding()
    )
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
fun SideNavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 20.dp, end = 16.dp)
            )
        }
    }
}

/**
 * Transparent top bar overlay for the hero panel: gold hamburger pill
 * (toggles the nav column), the AraToons logo (Ara white + Toons gold)
 * and a gold search pill (hero_section.jpg style).
 */
@Composable
fun TopBarOverlay(
    onToggleNav: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unit = remember { Unit }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Gold hamburger pill
        Box(
            modifier = Modifier
                .width(84.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primary)
                .focusable()
                .pointerInput(unit) {
                    detectTapGestures(onTap = { onToggleNav() })
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(2.dp)
                            .background(Color.White)
                    )
                    if (i < 2) Spacer(Modifier.height(3.dp))
                }
            }
        }
        Spacer(Modifier.width(20.dp))
        // Logo
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Ara",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = Color.White
            )
            Text(
                text = "Toons",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.weight(1f))
        // Search pill
        Row(
            modifier = Modifier
                .width(200.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(18.dp)
                )
                .focusable()
                .pointerInput(unit) {
                    detectTapGestures(onTap = { onSearch() })
                }
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.search_hint),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = Color(0xFFBDBDBD),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.search),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Transparent side navigation overlay for the hero panel: icon + label
 * rows (Home / Movies / Series / Search) with Settings pinned to the
 * bottom. Replaces the old opaque sidebar.
 */
@Composable
fun SideNavOverlay(
    onHome: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = 16.dp, top = 76.dp, bottom = 16.dp)
    ) {
        OverlayNavItem(
            icon = Icons.Filled.Home,
            label = stringResource(R.string.home),
            onClick = onHome
        )
        OverlayNavItem(
            icon = Icons.Filled.Movie,
            label = stringResource(R.string.movies),
            onClick = onMovies
        )
        OverlayNavItem(
            icon = Icons.Filled.AutoAwesome,
            label = stringResource(R.string.series),
            onClick = onSeries
        )
        OverlayNavItem(
            icon = Icons.Filled.Search,
            label = stringResource(R.string.search),
            onClick = onSearch
        )
        Spacer(Modifier.weight(1f))
        OverlayNavItem(
            icon = Icons.Filled.Settings,
            label = stringResource(R.string.settings),
            onClick = onSettings
        )
    }
}

@Composable
private fun OverlayNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val unit = remember { Unit }
    var active by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .focusable()
            .onFocusChanged { active = it.hasFocus }
            .pointerInput(unit) {
                detectTapGestures(onTap = { onClick() })
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                Color(0xFFE0E0E0)
            },
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = if (active) Color.White else Color(0xFFE0E0E0)
        )
    }
}

/**
 * Horizontal rail of poster cards with a bold white header and
 * ‹ / › arrow buttons that scroll the rail (hero_section.jpg style).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ContentRail(
    title: String,
    items: List<VideoItem>,
    onItem: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 108.dp,
    firstFocusRequester: FocusRequester? = null,
    showArrows: Boolean = true
) {
    val state = rememberLazyListState()
    val canBack by remember {
        derivedStateOf { state.firstVisibleItemIndex > 0 }
    }
    val canFwd by remember {
        derivedStateOf {
            state.firstVisibleItemIndex + state.layoutInfo.visibleItemsInfo.size < items.size
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            if (showArrows) {
                RailArrowButton(canScroll = canBack, direction = -1, state = state)
                RailArrowButton(canScroll = canFwd, direction = 1, state = state)
            }
        }
        LazyRow(
            state = state,
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer()
        ) {
            itemsIndexed(
                items,
                key = { _, item -> item.url + item.title + item.id }
            ) { index, item ->
                PosterCard(
                    item = item,
                    widthDp = cardWidth,
                    onClick = { onItem(item) },
                    modifier = if (index == 0 && firstFocusRequester != null) {
                        Modifier.focusRequester(firstFocusRequester)
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

/**
 * Small circular ‹ / › button that scrolls the rail by a page.
 */
@Composable
private fun RailArrowButton(
    canScroll: Boolean,
    direction: Int,
    state: LazyListState,
    modifier: Modifier = Modifier
) {
    val unit = remember { Unit }
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .padding(horizontal = 3.dp)
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .focusable()
            .pointerInput(unit, canScroll) {
                detectTapGestures(onTap = {
                    val last = (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                    val target = (state.firstVisibleItemIndex + direction * 5).coerceIn(0, last)
                    scope.launch { state.animateScrollToItem(target) }
                })
            }
    ) {
        Icon(
            imageVector = if (direction < 0) {
                Icons.Filled.KeyboardArrowLeft
            } else {
                Icons.Filled.KeyboardArrowRight
            },
            contentDescription = null,
            tint = Color.White.copy(alpha = if (canScroll) 1f else 0.4f),
            modifier = Modifier
                .align(Alignment.Center)
                .size(20.dp)
        )
    }
}

/**
 * Blurred copy of [model], visible only in the bottom text zone: the
 * blur fades in from [rampStart] to full strength at [rampFull] (fractions
 * of the height), with a warm dark gradient on top for text legibility.
 *
 * Works on all API levels (BlurMaskFilter on a downsampled bitmap).
 */
@Composable
fun BlurredImage(
    model: String,
    modifier: Modifier = Modifier,
    blurRadius: Float = 12f,
    rampStart: Float = 0.55f,
    rampFull: Float = 0.82f,
    darkColor: Color = Color(0xE61A1210),
    fullFrame: Boolean = false,
    alpha: Float = 1f
) {
    val context = LocalContext.current
    var bmp by remember(model, fullFrame) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(model, fullFrame) {
        // One-shot Coil loads don't retry; a transient CDN timeout would
        // leave the background black forever. Retry a few times — later
        // attempts usually hit the disk cache filled by AsyncImage.
        repeat(4) { attempt ->
            if (bmp != null) return@LaunchedEffect
            try {
                val drawable = Coil.imageLoader(context).execute(
                    ImageRequest.Builder(context).data(model).build()
                ).drawable
                android.util.Log.d("BlurredImage", "loaded: $drawable for $model")
                bmp = (drawable as? BitmapDrawable)?.bitmap?.let {
                    prepareBlurZone(it, blurRadius, rampStart, rampFull, darkColor.toArgb(), fullFrame)
                }
            } catch (e: Exception) {
                android.util.Log.w("BlurredImage", "attempt $attempt failed for $model", e)
                bmp = null
            }
            if (bmp != null) {
                android.util.Log.d("BlurredImage", "prepared ${bmp!!.width}x${bmp!!.height}")
            }
            if (bmp == null && attempt < 3) delay(2000)
        }
    }
    Canvas(modifier = modifier) {
        val b = bmp ?: return@Canvas
        val img = b.asImageBitmap()
        // ContentScale.Crop, centered
        val s = max(size.width / b.width, size.height / b.height)
        val dw = b.width * s
        val dh = b.height * s
        val dx = (size.width - dw) / 2f
        val dy = (size.height - dh) / 2f
        drawImage(img, Offset(dx, dy), alpha = alpha)
    }
}

/**
 * Downsample + BlurMaskFilter (all API levels), then bake the text zone
 * into the bitmap: alpha ramps from 0 to full between [rampStart] and
 * [rampFull] (fractions of the height) and a warm dark gradient sits on
 * top for text legibility. Compose then just draws the bitmap — no
 * canvas state tricks, no API-level gates.
 */
private fun prepareBlurZone(
    src: Bitmap,
    radius: Float,
    rampStart: Float,
    rampFull: Float,
    darkArgb: Int,
    fullFrame: Boolean
): Bitmap {
    // Coil may hand back a HARDWARE bitmap; it can't be drawn onto a
    // software canvas, so convert first (this was silently killing the
    // blur — the catch swallowed the exception and the zone stayed null).
    val srcSw = if (src.config == Bitmap.Config.HARDWARE) {
        src.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        src
    }
    val scale = 4f
    val w = (srcSw.width / scale).toInt().coerceAtLeast(1)
    val h = (srcSw.height / scale).toInt().coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(srcSw, w, h, true)
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = GCanvas(out)
    val blurPaint = Paint().apply {
        isFilterBitmap = true
        maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawBitmap(small, 0f, 0f, blurPaint)
    small.recycle()
    if (fullFrame) return out
    // Alpha ramp: 0 above rampStart, 0 -> 255 between rampStart..rampFull
    val ramp = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        shader = LinearGradient(
            0f, h * rampStart, 0f, h * rampFull,
            0x00FFFFFF, 0xFFFFFFFF, Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), ramp)
    // Warm dark gradient for text legibility
    val dark = Paint().apply {
        shader = LinearGradient(
            0f, h * rampStart, 0f, h.toFloat(),
            0x00000000, darkArgb, Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), dark)
    return out
}

/**
 * Poster card (hero_section.jpg style): the poster fills the card and the
 * title sits below it in white — no overlay text, so no blur needed.
 *
 * Thin bright border + gold border & springy zoom on focus.
 */
@Composable
fun PosterCard(
    item: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    widthDp: Dp = 108.dp
) {
    val focusRequester = remember { FocusRequester() }
    var active by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (active) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.72f)
    )
    val cardShape = RoundedCornerShape(10.dp)
    val unit = remember { Unit }

    Column(
        modifier = modifier
            .width(widthDp)
            .zIndex(if (active) 1f else 0f)
            .scale(scale)
            .focusable()
            .focusRequester(focusRequester)
            .onFocusChanged { active = it.hasFocus }
            .pointerInput(unit) {
                detectTapGestures(onTap = { onClick() })
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .shadow(
                    elevation = if (active) 12.dp else 6.dp,
                    shape = cardShape,
                    ambientColor = Color.Black.copy(alpha = 0.25f),
                    spotColor = Color.Black.copy(alpha = 0.4f)
                )
                .background(Color(0xFF2A2D31), cardShape)
                .border(
                    width = if (active) 2.dp else 1.dp,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.White.copy(alpha = 0.18f)
                    },
                    shape = cardShape
                )
                .clip(cardShape)
        ) {
            if (item.posterUrl.isNotEmpty()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(32.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Menu card for the home screen sidebar.
 */
@Composable
fun MenuCard(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val unit = remember { Unit }
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .focusRequester(focusRequester)
            .pointerInput(unit) {
                detectTapGestures(onTap = { onClick() })
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

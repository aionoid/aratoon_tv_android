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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
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

/**
 * Horizontal content rail (stardima_tv_flutter ContentRail): a bold rail
 * title on top and a row of 88dp-wide poster cards. No arrow buttons —
 * the D-pad drives horizontal/vertical rail focus, crossing rail
 * boundaries explicitly: UP at the top rail goes to the immersive preview,
 * LEFT at the leftmost card of any rail goes to the bottom tab bar,
 * UP/DOWN between rails move to the same card index of the neighbouring
 * rail (clamped), DOWN on the bottom rail goes to the tab bar, RIGHT at
 * the rightmost card wraps around to the leftmost (ring, standard TV
 * carousel), and ENTER opens the item (see
 * android.com/training/tv/get-started/navigation + "Android TV D-pad
 * Navigation Handling in Jetpack Compose").
 */
@Composable
fun ContentRail(
    title: String,
    items: List<VideoItem>,
    onItem: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 88.dp,
    railIndex: Int = 0,
    railCount: Int = 1,
    prevCount: Int = 0,
    nextCount: Int = 0,
    previewFocus: FocusRequester?,
    navEntry: FocusRequester? = null,
    onCardFocus: ((VideoItem, Boolean) -> Unit)? = null,
    cardNodes: MutableMap<Int, FocusRequester> = remember { mutableMapOf() },
    railStates: MutableMap<Int, LazyListState> = remember { mutableMapOf() },
    railColumnState: LazyListState? = null
) {
    val state = railStates.getOrPut(railIndex) { LazyListState() }
    val scope = rememberCoroutineScope()
    // In RTL the rail is laid out right-to-left: physical LEFT moves focus to
    // a higher index and physical RIGHT to a lower one (opposite of LTR); the
    // rail-edge wrap and "escape to tab bar" swap accordingly.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(items, key = { _, item -> item.url + item.title + item.id }) { index, item ->
                val nodeKey = railIndex * 1000 + index
                val fr = remember { FocusRequester() }
                // Keep [cardNodes] holding ONLY currently-composed requesters:
                // requestFocus() on a requester whose node left composition
                // throws IllegalStateException ("FocusRequester is not
                // initialized"), which crashed the app when a D-pad key
                // targeted a card in a rail below the fold.
                DisposableEffect(fr) {
                    cardNodes[nodeKey] = fr
                    onDispose { cardNodes.remove(nodeKey) }
                }
                PosterCard(
                    item = item,
                    onClick = { onItem(item) },
                    widthDp = cardWidth,
                    externalFocusRequester = fr,
                    onKey = { event ->
                        if (event.type != KeyEventType.KeyDown) return@PosterCard false
                        fun focusCard(key: Int) {
                            val node = cardNodes[key]
                            if (node != null) {
                                android.util.Log.d("FocusMap", "focusCard rail=${key / 1000} idx=${key % 1000} DIRECT")
                                node.requestFocus()
                            } else {
                                android.util.Log.d("FocusMap", "focusCard rail=${key / 1000} idx=${key % 1000} COROUTINE(not-composed)")
                                // Card not composed yet (rail below/above the
                                // fold): first scroll the vertical column so
                                // the target rail is composed, then scroll
                                // the rail so the target card is composed,
                                // then focus it.
                                val targetRail = key / 1000
                                val target = key % 1000
                                scope.launch {
                                    railColumnState?.scrollToItem(targetRail)
                                    delay(120)
                                    railStates[targetRail]?.scrollToItem(target)
                                    delay(80)
                                    cardNodes[key]?.requestFocus()
                                }
                            }
                        }
                        when (event.key) {
                            Key.DirectionLeft -> if (rtl) {
                                if (index < items.size - 1) {
                                    focusCard(nodeKey + 1)
                                    true
                                } else {
                                    // RTL leftmost = highest index: wrap to the
                                    // start of the rail (the right edge).
                                    focusCard(railIndex * 1000)
                                    true
                                }
                            } else when {
                                index > 0 -> {
                                    focusCard(nodeKey - 1)
                                    true
                                }
                                else -> {
                                    // Leftmost card: nothing to the left.
                                    // Escaping to the bottom tab bar keeps
                                    // focus inside the shell (never into
                                    // the default nearest-element traversal)
                                    navEntry?.requestFocus()
                                    true
                                }
                            }
                            Key.DirectionRight -> if (rtl) {
                                if (index > 0) {
                                    focusCard(nodeKey - 1)
                                    true
                                } else {
                                    // RTL rightmost = index 0: escape to tab bar.
                                    navEntry?.requestFocus()
                                    true
                                }
                            } else if (index < items.size - 1) {
                                focusCard(nodeKey + 1)
                                true
                            } else {
                                // Wrap-around (ring): rightmost card -> leftmost
                                // card, so focus never falls off the rail edge
                                // into the default (nearest-element) traversal
                                focusCard(railIndex * 1000)
                                true
                            }
                            Key.DirectionUp -> when {
                                railIndex == 0 -> {
                                    // Top rail: above is the immersive
                                    // preview block
                                    previewFocus?.requestFocus()
                                    true
                                }
                                else -> {
                                    focusCard((railIndex - 1) * 1000 + index.coerceIn(0, prevCount - 1))
                                    true
                                }
                            }
                            Key.DirectionDown -> if (railIndex < railCount - 1) {
                                focusCard((railIndex + 1) * 1000 + index.coerceIn(0, nextCount - 1))
                                true
                            } else {
                                // Bottom rail: below is the bottom tab bar
                                navEntry?.requestFocus()
                                true
                            }
                            else -> false
                        }
                    },
                    onFocus = { hasFocus ->
                        if (hasFocus) {
                            android.util.Log.d("FocusMap", "CARD focus rail=$railIndex idx=$index")
                            onCardFocus?.invoke(item, hasFocus)
                            // Keep the focused card visible in its rail…
                            val first = state.firstVisibleItemIndex
                            val visible = state.layoutInfo.visibleItemsInfo.size
                            if (visible > 0 && (index < first || index >= first + visible)) {
                                scope.launch { state.scrollToItem(index) }
                            }
                            // …and the rail visible in the vertical column.
                            railColumnState?.let { col ->
                                val colFirst = col.firstVisibleItemIndex
                                if (railIndex < colFirst || railIndex >= colFirst + 2) {
                                    scope.launch { col.animateScrollToItem(railIndex) }
                                }
                            }
                        }
                    }
                )
            }
        }
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
                // Cap the load at 900px (Flutter: memCacheWidth 900) — the
                // raw hero posters are only a few hundred px wide, and a
                // full-size load + heavy downsample is what made the
                // background look pixelated.
                val drawable = Coil.imageLoader(context).execute(
                    ImageRequest.Builder(context)
                        .data(model)
                        .size(900, 900)
                        .build()
                ).drawable
                bmp = (drawable as? BitmapDrawable)?.bitmap?.let {
                    prepareBlurZone(it, blurRadius, rampStart, rampFull, darkColor.toArgb(), fullFrame)
                }
            } catch (e: Exception) {
                bmp = null
            }
            if (bmp == null && attempt < 3) delay(2000)
        }
    }
    Canvas(modifier = modifier) {
        val b = bmp ?: return@Canvas
        val img = b.asImageBitmap()
        // ContentScale.Crop, centered — the size MUST be passed explicitly,
        // otherwise drawImage uses the bitmap's native (tiny) size while the
        // offset assumes the scaled size, painting off-screen (all-black bg).
        // The extra 12% (Flutter: Transform.scale(1.15)) pushes the
        // BlurMaskFilter's transparent edge fade off-screen.
        val s = max(size.width / b.width, size.height / b.height) * 1.12f
        val dw = (b.width * s).toInt().coerceAtLeast(1)
        val dh = (b.height * s).toInt().coerceAtLeast(1)
        val dx = ((size.width - b.width * s) / 2f).toInt()
        val dy = ((size.height - b.height * s) / 2f).toInt()
        // ContentScale.Crop, centered (full-source → scaled dest rect)
        drawImage(
            image = img,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(b.width, b.height),
            dstOffset = IntOffset(dx, dy),
            dstSize = IntSize(dw, dh),
            alpha = alpha
        )
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
    // 1/2 scale (was 1/4): the blur radius is then proportional to the
    // upscaled display size, so the result stays smooth instead of pixelated.
    val scale = 2f
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
    widthDp: Dp = 108.dp,
    externalFocusRequester: FocusRequester? = null,
    onKey: ((KeyEvent) -> Boolean)? = null,
    onFocus: ((Boolean) -> Unit)? = null
) {
    val focusRequester = externalFocusRequester ?: remember { FocusRequester() }
    var active by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (active) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.72f)
    )
    val cardShape = RoundedCornerShape(10.dp)
    val gold = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .width(widthDp)
            .zIndex(if (active) 1f else 0f)
            .scale(scale)
            .drawBehind {
                // Soft gold glow ring around the whole focused item
                if (active) {
                    val pad = 3.dp.toPx()
                    drawRoundRect(
                        color = gold.copy(alpha = 0.35f),
                        topLeft = Offset(-pad, -pad),
                        size = Size(size.width + 2 * pad, size.height + 2 * pad),
                        cornerRadius = CornerRadius(14.dp.toPx())
                    )
                }
            }
            // Observe focus from OUTSIDE the focusable node: onFocusChanged
            // only aggregates focus targets that follow it in the chain, so
            // it must come before focusRequester/clickable or `active`
            // (zoom, glow, scroll-into-view) never turns on
            .onFocusChanged {
                android.util.Log.d("FocusMap", "PosterCard focusChanged hasFocus=${it.hasFocus} item=${item.title.take(24)}")
                active = it.hasFocus
                onFocus?.invoke(it.hasFocus)
            }
            // clickable() so D-pad CENTER opens the item
            .focusRequester(focusRequester)
            .then(onKey?.let { Modifier.onKeyEvent(it) } ?: Modifier)
            .clickable(interactionSource = null, indication = null) { onClick() }
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
                    width = if (active) 3.dp else 1.dp,
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageDropdown(
    current: Int,
    lastPage: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = stringResource(R.string.page_indicator, current, lastPage),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            for (p in 1..lastPage) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (p == current) {
                                stringResource(R.string.page_current, p)
                            } else {
                                stringResource(R.string.page_menu_item, p)
                            }
                        )
                    },
                    onClick = {
                        expanded = false
                        onPick(p)
                    }
                )
            }
        }
    }
}


/**
 * Page number rail for catalogs (stardima_tv_flutter catalog screen): a
 * ‹ arrow, a window of tappable page numbers (1 … n-1 n n+1 … last) and a
 * › arrow, all in a glass pill bar.
 */
@Composable
fun CatalogPageBar(
    page: Int,
    lastPage: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.3f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.15f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CatalogPageArrow(Icons.Filled.KeyboardArrowLeft, page > 1, onPrev)
        Spacer(Modifier.width(6.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            pageWindow(page, lastPage).forEach { p ->
                if (p < 0) {
                    Text(
                        "…",
                        color = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                } else {
                    // 4dp on each side = 8dp gap between the number tiles
                    CatalogPageNumber(
                        p,
                        p == page,
                        onClick = { onPage(p) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        CatalogPageArrow(Icons.Filled.KeyboardArrowRight, page < lastPage, onNext)
    }
}

/**
 * Page number window centred on [page], 7-slot cap, first and last pages
 * pinned with ellipsis gaps (matches the Flutter catalog pagination).
 */
private fun pageWindow(page: Int, last: Int): List<Int> {
    if (last <= 7) return (1..last).toList()
    val out = mutableListOf(1)
    if (page > 3) out.add(-1)
    for (p in (page - 1)..(page + 1)) {
        if (p >= 2 && p <= last - 1) out.add(p)
    }
    if (page < last - 2) out.add(-1)
    out.add(last)
    return out
}

/**
 * Glass arrow button (34x34) of the catalog page bar.
 */
@Composable
private fun CatalogPageArrow(
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.72f)
    )
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .size(34.dp)
            .scale(scale)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f), shape)
            .border(
                1.dp,
                if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f),
                shape
            )
            // onFocusChanged before clickable() — see PosterCard
            .onFocusChanged { focused = it.hasFocus }
            .clickable(interactionSource = null, indication = null, enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.25f),
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Tappable page number (34x34). The current page is a gold tile.
 */
@Composable
private fun CatalogPageNumber(
    page: Int,
    current: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.72f)
    )
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .size(34.dp)
            .scale(scale)
            .clip(shape)
            .background(
                if (current) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.06f),
                shape
            )
            .border(
                1.dp,
                if (current) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f),
                shape
            )
            // onFocusChanged before clickable() — see PosterCard
            .onFocusChanged { focused = it.hasFocus }
            .clickable(interactionSource = null, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            page.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold
            ),
            color = if (current) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.7f)
        )
    }
}

/**
 * Small glass pill button (player bar actions): gold fill for primary
 * actions, translucent glass otherwise, with a focus zoom.
 */
@Composable
fun ActionPill(
    label: String,
    gold: Boolean = false,
    small: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val goldColor = MaterialTheme.colorScheme.primary
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.72f)
    )
    val height = if (small) 30.dp else 36.dp
    val shape = RoundedCornerShape(height / 2)
    Row(
        modifier = modifier
            .height(height)
            .scale(scale)
            .clip(shape)
            .background(
                if (gold) goldColor else Color.White.copy(alpha = 0.06f),
                shape
            )
            .border(
                width = if (focused && !gold) 1.5.dp else 0.dp,
                color = goldColor,
                shape = shape
            )
            // onFocusChanged before clickable() — see PosterCard
            .onFocusChanged { focused = it.hasFocus }
            .clickable(interactionSource = null, indication = null) { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = if (small) 11.sp else 12.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = if (gold) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.85f)
        )
    }
}

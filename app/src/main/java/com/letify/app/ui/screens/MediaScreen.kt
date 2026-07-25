@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.letify.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.letify.app.ui.components.BackButton
import com.letify.app.ui.components.BackChevron
import com.letify.app.ui.components.rememberElasticOverscroll
import com.letify.app.ui.components.ElasticOverscroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.ExperimentalFoundationApi
import com.letify.app.ui.components.NoFeedbackButton
import com.letify.app.ui.icons.SolarIcon
import com.letify.app.ui.state.Dates
import com.letify.app.ui.state.LocalAppState
import com.letify.app.ui.state.MediaItem
import com.letify.app.ui.theme.Letify
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val FlySpec = tween<Float>(durationMillis = 480)

@Composable
fun MediaScreen(
    onBack: () -> Unit,
    onOpenCamera: () -> Unit,
) {
    val state = LocalAppState.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { state.reloadMedia(context.filesDir) }

    var selectedId by remember { mutableStateOf<String?>(null) }
    var sourceBounds by remember { mutableStateOf<Rect?>(null) }
    var hideTileId by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    // Live bounds of every grid tile — close-after-switch flies back to the
    // *currently selected* photo's cell, not the original tap target.
    val tileBounds = remember { mutableStateMapOf<String, Rect>() }

    val selected = selectedId?.let { id -> state.mediaItems.find { it.id == id } }
    val dayItems = selected?.let { s ->
        state.mediaItems.filter { dateKeyOf(it.createdAt) == dateKeyOf(s.createdAt) }
    }.orEmpty()
    val photoCount = state.mediaItems.count { !it.isVideo }
    val videoCount = state.mediaItems.count { it.isVideo }

    val screenW = with(density) { config.screenWidthDp.dp.toPx() }
    val screenH = with(density) { config.screenHeightDp.dp.toPx() }
    val statusBarPx = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerBottomPx = with(density) { (statusBarPx + 76.dp).toPx() }

    fun resolveSourceBounds(raw: Rect?): Rect? {
        if (raw == null || raw == Rect.Zero) return null
        return flightSourceBounds(raw, screenW, screenH, headerBottomPx)
    }

    // Shared open/close progress, lifted out of the day host so the LIST
    // header — which stays mounted the whole time — can cross-fade itself
    // against it too. Previously the day host owned this Animatable, so it
    // only existed while a day was open; the list header had no way to
    // react to it and was simply hard-covered by the day view's opaque
    // background, then popped back in the instant the day view unmounted —
    // that hard pop was the "заголовок мигает" glitch. Now both headers
    // read the same value and fade in lockstep, so nothing pops.
    val dayProgress = remember { Animatable(0f) }
    var closingDay by remember { mutableStateOf(false) }

    // Real sampled backdrop for the header island + back button "liquid glass"
    // below — the grid (and day sheet) draw into this source, and the header
    // reads it through `hazeChild` for a genuine blurred/refracted glass look
    // instead of a flat tint.
    val hazeState = remember { HazeState() }

    // Keyed on the null↔non-null transition only (not on `selected` itself),
    // so switching photos inside an already-open day via the strip below
    // does NOT re-run this and restart the fly-in from scratch.
    LaunchedEffect(selected != null) {
        if (selected != null) {
            closingDay = false
            dayProgress.snapTo(0f)
            dayProgress.animateTo(1f, FlySpec)
        }
    }

    fun closeDay() {
        if (closingDay || selectedId == null) return
        closingDay = true
        scope.launch {
            dayProgress.animateTo(0f, FlySpec)
            selectedId = null
            hideTileId = null
            sourceBounds = null
            closingDay = false
        }
    }

    Box(Modifier.fillMaxSize().background(Letify.colors.bg)) {
        Box(Modifier.fillMaxSize().haze(hazeState)) {
            MomentsListScreen(
                items = state.mediaItems.toList(),
                hideTileId = hideTileId,
                onOpenCamera = onOpenCamera,
                onTileBounds = { id, rect -> tileBounds[id] = rect },
                onOpenItem = { item, bounds ->
                    val src = resolveSourceBounds(bounds)
                    sourceBounds = src
                    // Only hide the grid cell when we actually fly from it —
                    // a fade-open (src == null) must leave the cell visible so
                    // there is no empty hole under a partially scrolled tile.
                    hideTileId = if (src != null) item.id else null
                    selectedId = item.id
                },
            )

            if (selected != null) {
                MomentDayHost(
                    item = selected,
                    dayItems = dayItems,
                    progress = dayProgress,
                    sourceBounds = sourceBounds,
                    onSelect = { next ->
                        selectedId = next.id
                        val src = resolveSourceBounds(tileBounds[next.id])
                        sourceBounds = src
                        // Hide the newly selected grid cell only when we can fly
                        // back to it; otherwise leave every cell visible.
                        hideTileId = if (src != null) next.id else null
                    },
                    onBack = { closeDay() },
                    onOpenEditor = { showEditor = true },
                )
            }
        }

        // Header — lives above BOTH the grid and the day view (highest
        // zIndex in this whole screen), so the flying photo always passes
        // UNDER it, never over it, no matter where its real bounds are.
        // It cross-fades against the same `dayProgress` the fly uses
        // instead of relying on the day view's own overlay to hide it, so
        // it never disappears/reappears in a single frame.
        MomentsHeader(
            photoCount = photoCount,
            videoCount = videoCount,
            progress = dayProgress,
            interactive = selectedId == null,
            onBack = onBack,
            hazeState = hazeState,
        )

        if (showEditor && selected != null) {
            NoteEditorScreen(
                initial = selected.note,
                onCancel = { showEditor = false },
                onSave = { text ->
                    state.setMediaNote(selected.id, text)
                    showEditor = false
                },
            )
        }
    }
}

// ── List ───────────────────────────────────────────────────────────────────

@Composable
private fun MomentsListScreen(
    items: List<MediaItem>,
    hideTileId: String?,
    onOpenCamera: () -> Unit,
    onTileBounds: (String, Rect) -> Unit,
    onOpenItem: (MediaItem, Rect) -> Unit,
) {
    val groups = remember(items) { groupByDay(items) }
    // Real status-bar height, used as a content-padding offset (not a
    // windowInsetsPadding on the grid itself) — see the grid below.
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 76.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(64.dp).background(Letify.colors.container, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        SolarIcon(name = "gallery-bold-duotone", tint = Letify.colors.muted, size = 28.dp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Пока пусто", color = Letify.colors.text, style = Letify.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Нажми + чтобы сделать фото\nили удерживай для видео",
                        color = Letify.colors.muted,
                        style = Letify.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            ElasticOverscroll {
            // No windowInsetsPadding(statusBars) here on purpose: that would
            // wall this box off below the status bar, so the area behind it
            // could never show a scrolled-up photo — just the flat screen
            // background sitting there permanently, which is exactly the
            // opaque black band being reported. Every other screen gets
            // away with the inset padding because its background color IS
            // the screen color, so the band is invisible there; a photo grid
            // needs to actually scroll its content underneath the
            // (transparent) status bar, so the grid fills the whole screen
            // and only the starting content offset comes from contentPadding.
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = statusBarInset + 76.dp, bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalItemSpacing = 6.dp,
            ) {
                groups.forEach { (label, dayItems) ->
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Text(
                            label,
                            color = Letify.colors.muted,
                            style = Letify.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 4.dp),
                        )
                    }
                    items(dayItems, key = { it.id }) { item ->
                        MomentTile(
                            item = item,
                            hidden = item.id == hideTileId,
                            onBounds = { rect -> onTileBounds(item.id, rect) },
                            onClick = { bounds -> onOpenItem(item, bounds) },
                        )
                    }
                }
            }
            } // ElasticOverscroll
        } // else

        NoFeedbackButton(
            onClick = onOpenCamera,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
                .size(54.dp)
                .zIndex(3f)
                .clip(RoundedCornerShape(16.dp))
                .background(Letify.colors.accent),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("+", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// A tasteful approximation of iOS 26 "Liquid Glass": a real sampled +
// blurred backdrop (via Haze, reading `hazeState` filled in by the grid/day
// content below) instead of a flat tint, plus a bright top-to-bottom rim
// highlight — the cue that reads as convex glass catching light rather than
// plain frosted plastic. `shape` drives both the blur's clip and the rim.
// Deliberately built on just the `hazeChild(state, shape)` overload — the
// one signature that's been stable across every Haze release since 0.4.0 —
// with the tint and shine layered on top as plain modifiers, rather than
// HazeStyle/HazeTint, whose constructor shape has changed release to
// release and doesn't match the pinned 0.7.3 build here.
private fun Modifier.liquidGlass(hazeState: HazeState, shape: Shape): Modifier = this
    .clip(shape)
    .hazeChild(state = hazeState, shape = shape)
    .background(Color.Black.copy(alpha = 0.30f), shape)
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f)),
        ),
        shape = shape,
    )

// Header — a separate top-level layer (sibling of both the grid and the day
// view in MediaScreen's Box), always mounted and drawn above everything else
// in this screen. It cross-fades against `dayProgress` instead of being
// hard-covered/uncovered by the day view's own opaque overlay, which is what
// used to make it pop in/out in a single frame ("заголовок мигает"). Because
// it sits above the flying photo at every moment, the photo simply flies in
// UNDER it — no need to clamp the photo's flight bounds to dodge it.
@Composable
private fun MomentsHeader(
    photoCount: Int,
    videoCount: Int,
    progress: Animatable<Float, AnimationVector1D>,
    interactive: Boolean,
    onBack: () -> Unit,
    hazeState: HazeState,
) {
    val countLabel = buildString {
        if (photoCount > 0) append("$photoCount фото")
        if (photoCount > 0 && videoCount > 0) append(" · ")
        if (videoCount > 0) append("$videoCount видео")
    }
    Box(
        Modifier
            .fillMaxWidth()
            .zIndex(20f)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 6.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp)
                .size(44.dp)
                .graphicsLayer { alpha = 1f - progress.value }
                .liquidGlass(hazeState, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // When a day is open (interactive == false) this whole hot zone
            // must NOT be part of the composition at all — not just have its
            // onClick disabled. A disabled Modifier.clickable still consumes
            // the tap so it never reaches whatever is underneath, which is
            // exactly what silently ate every tap on the open moment card's
            // own back button (same top-left corner, lower z-order). Falling
            // back to a bare, non-clickable chevron here lets touches pass
            // straight through to that button.
            if (interactive) {
                BackButton(
                    onClick = onBack,
                    tint = Color.White,
                    glyphSize = 20.dp,
                    tapSize = 44.dp,
                )
            } else {
                BackChevron(tint = Color.White, size = 20.dp)
            }
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = 1f - progress.value }
                .liquidGlass(hazeState, RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Моменты",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            if (countLabel.isNotEmpty()) {
                Text(
                    countLabel,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun MomentTile(
    item: MediaItem,
    hidden: Boolean,
    onBounds: (Rect) -> Unit,
    onClick: (Rect) -> Unit,
) {
    val ratio = item.aspectRatio.coerceIn(0.55f, 1.4f)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val shape = RoundedCornerShape(14.dp)
    NoFeedbackButton(
        onClick = { if (bounds != Rect.Zero) onClick(bounds) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        // When hidden (shared-element flight in progress) the tile must be
        // fully invisible — no near-black placeholder disc that used to flash
        // under the flying photo and peek as a 1px edge while scrolling.
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .onGloballyPositioned {
                    val r = it.boundsInRoot()
                    bounds = r
                    onBounds(r)
                }
                .graphicsLayer {
                    alpha = if (hidden) 0f else 1f
                    clip = true
                    this.shape = shape
                }
                .background(Letify.colors.container, shape),
        ) {
            if (!hidden) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(mediaDisplayUri(item))
                        .memoryCacheKey(item.id)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                if (item.isVideo) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("▶", color = Color.White, fontSize = 22.sp)
                    }
                }
                if (item.note.isNotBlank()) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(7.dp)
                            .background(Letify.colors.accent, CircleShape),
                    )
                }
                val time = formatTime(item.createdAt)
                Text(
                    if (item.isVideo && item.durationLabel.isNotBlank()) item.durationLabel else time,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
    }
}

// ── Day host with fly + sheet ──────────────────────────────────────────────

@Composable
private fun MomentDayHost(
    item: MediaItem,
    dayItems: List<MediaItem>,
    progress: Animatable<Float, AnimationVector1D>,
    sourceBounds: Rect?,
    onSelect: (MediaItem) -> Unit,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current

    // Fixed destination — never remeasure mid-flight.
    val dst = with(density) {
        val maxH = (config.screenHeightDp * 0.52f).dp.toPx()
        val w = (config.screenWidthDp.dp - 32.dp).toPx()
        val ratio = item.aspectRatio.coerceIn(0.55f, 1.6f)
        val h = (w / ratio).coerceAtMost(maxH)
        val left = 16.dp.toPx()
        val top = 98.dp.toPx()
        Rect(left, top, left + w, top + h)
    }

    // The open/close drive (`progress`) and the once-per-session LaunchedEffect
    // now live in MediaScreen — shared with MomentsHeader so both cross-fade
    // together instead of the header being hard-covered/uncovered in one frame.

    Box(Modifier.fillMaxSize().zIndex(10f)) {
        // We pass the Animatable itself, not `.value` — reading `.value` here
        // would make THIS composable re-run every animation frame, and with
        // it the entire heavy MomentDayContent tree below (stats, tasks,
        // meals, day strip — dozens of composables) at 60fps. That was the
        // real cause of the lag and the "wrong size on close" glitch: frames
        // were being dropped under that recomposition load. MomentDayContent
        // only reads `.value` inside layout/graphicsLayer lambdas (the draw
        // & layout phases), which re-run cheaply without recomposing.
        MomentDayContent(
            item = item,
            dayItems = dayItems,
            progress = progress,
            sourceBounds = sourceBounds,
            heroBounds = dst,
            onBack = onBack,
            onSelect = onSelect,
            onOpenEditor = onOpenEditor,
        )
    }
}

@Composable
private fun MomentDayContent(
    item: MediaItem,
    dayItems: List<MediaItem>,
    progress: Animatable<Float, AnimationVector1D>,
    sourceBounds: Rect?,
    heroBounds: Rect,
    onBack: () -> Unit,
    onSelect: (MediaItem) -> Unit,
    onOpenEditor: () -> Unit,
) {
    val state = LocalAppState.current
    val density = LocalDensity.current
    val dateKey = dateKeyOf(item.createdAt)
    val today = Dates.todayKey()
    val isToday = dateKey == today
    val weight = state.weightLog.find { it.dateKey == dateKey }?.kg
    val waterMl = state.waterEntriesOn(dateKey).sumOf { it.ml }
    val sleep = state.sleepLog.find { it.dateKey == dateKey }
    val tasks = state.tasksOn(dateKey)
    val photoHeightPx = heroBounds.height
    val photoH = with(density) { photoHeightPx.toDp() }
    val scroll = rememberScrollState()
    val stripScroll = rememberScrollState()
    val elastic = rememberElasticOverscroll(maxVertical = 56.dp, maxHorizontal = 0.dp)
    val stripElastic = rememberElasticOverscroll(maxVertical = 0.dp, maxHorizontal = 48.dp)

    Box(Modifier.fillMaxSize()) {
        // Background fill fades in with the rest of the UI (never fully opaque
        // until the flight settles) — this is what lets the grid list show
        // through behind it while flying. Reading progress.value inside
        // graphicsLayer{} only invalidates this draw layer, not the whole
        // composable, so it stays cheap every frame.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value }.background(Letify.colors.bg))

        // ── The one photo — same element for the whole open transition ──
        // Position/size come from a layout()-phase read of progress.value
        // (measure only, no recomposition); scale/corner-radius come from a
        // graphicsLayer (draw phase, no recomposition either). It never
        // fades — it's a single element that MOVES from the tapped tile into
        // the hero slot, always fully opaque and visible.
        Box(
            Modifier
                .flightBounds(progress, sourceBounds, heroBounds)
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    val hasSrc = sourceBounds != null
                    // With a real source the photo stays fully opaque while it
                    // morphs; without one (partially off-screen tap) it fades
                    // in/out with the sheet so close doesn't hard-cut.
                    alpha = if (hasSrc) 1f else p
                    val scale = 1f - (scroll.value / photoHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f) * 0.12f
                    scaleX = scale
                    scaleY = scale
                    shape = RoundedCornerShape(if (hasSrc) 14.dp + (20.dp - 14.dp) * p else 20.dp)
                    clip = true
                }
                .background(Letify.colors.container),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(mediaDisplayUri(item))
                    // Same key the grid tile stored its bitmap under — shows
                    // instantly (already decoded, already on screen a moment
                    // ago). No crossfade: a fade would briefly reveal the
                    // container fill as a dark/black patch under the photo.
                    .placeholderMemoryCacheKey(item.id)
                    .memoryCacheKey(item.id)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (item.isVideo) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("▶", color = Color.White, fontSize = 36.sp)
                }
            }
        }

        // ── Scroll: spacer reveals photo, sheet rides up over it ──
        CompositionLocalProvider(
            LocalOverscrollConfiguration provides null,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value }
                    .nestedScroll(elastic.connection)
                    .graphicsLayer { translationY = elastic.verticalOverscroll.floatValue }
                    .verticalScroll(scroll),
            ) {
                // Transparent spacer = header + photo + gap (photo shows through)
                Spacer(Modifier.height(98.dp + photoH + 14.dp))

                // Sheet
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 520.dp)
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        .background(Letify.colors.bg)
                        .padding(bottom = 48.dp),
                ) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(Letify.colors.track),
                        )
                    }

                    Text(
                        formatDayTitle(dateKey),
                        color = Letify.colors.text,
                        style = Letify.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    )
                    Text(
                        formatTime(item.createdAt),
                        color = Letify.colors.muted,
                        style = Letify.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 3.dp, bottom = 14.dp),
                    )

                    if (dayItems.size > 1) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .nestedScroll(stripElastic.connection)
                                .graphicsLayer { translationX = stripElastic.horizontalOverscroll.floatValue }
                                .horizontalScroll(stripScroll)
                                .padding(bottom = 12.dp),
                        ) {
                            Spacer(Modifier.width(20.dp))
                            dayItems.forEach { m ->
                                val on = m.id == item.id
                                NoFeedbackButton(onClick = { onSelect(m) }) {
                                    Box(
                                        Modifier
                                            .padding(end = 8.dp)
                                            .width(64.dp)
                                            .height(84.dp)
                                            .then(
                                                if (on) Modifier.border(
                                                    2.5.dp,
                                                    Letify.colors.accent,
                                                    RoundedCornerShape(14.dp),
                                                ) else Modifier,
                                            )
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Letify.colors.container),
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(mediaDisplayUri(m))
                                                .memoryCacheKey(m.id)
                                                .placeholderMemoryCacheKey(m.id)
                                                .crossfade(120)
                                                .build(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                        }
                    }

                    NoFeedbackButton(
                        onClick = onOpenEditor,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Letify.colors.container)
                                .padding(14.dp),
                        ) {
                            Text(
                                item.note.ifBlank { "Нет заметки" },
                                color = if (item.note.isBlank()) Letify.colors.muted else Letify.colors.text,
                                style = Letify.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (item.note.isBlank()) "Добавить" else "Изменить",
                                color = Letify.colors.accent,
                                style = Letify.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Этот день",
                        color = Letify.colors.muted,
                        style = Letify.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DayStat(Modifier.weight(1f), weight?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "—", "кг")
                        DayStat(
                            Modifier.weight(1f),
                            if (waterMl > 0) {
                                val l = waterMl / 1000f
                                if (l >= 1f) String.format(java.util.Locale.US, "%.1f", l) else waterMl.toString()
                            } else "—",
                            if (waterMl >= 1000) "л" else "мл",
                        )
                        DayStat(Modifier.weight(1f), if (isToday && state.kcal > 0) state.kcal.toString() else "—", "ккал")
                        DayStat(Modifier.weight(1f), sleep?.let { formatSleep(it.durationMinutes) } ?: "—", "сон")
                    }

                    if (tasks.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        Text(
                            "План",
                            color = Letify.colors.muted,
                            style = Letify.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Letify.colors.container)
                                .padding(horizontal = 14.dp),
                        ) {
                            tasks.forEachIndexed { i, task ->
                                val done = task.isCompletedOn(dateKey)
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Letify.colors.bg),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        SolarIcon(name = task.icon, tint = task.color, size = 14.dp)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        task.name,
                                        color = Letify.colors.text,
                                        style = Letify.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                    )
                                    Text(
                                        if (done) "готово" else "—",
                                        color = if (done) Color(0xFF4ECB71) else Letify.colors.muted,
                                        style = Letify.typography.bodySmall,
                                        fontWeight = if (done) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                                if (i < tasks.lastIndex) {
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(Letify.colors.track))
                                }
                            }
                        }
                    }

                    if (isToday) {
                        Spacer(Modifier.height(18.dp))
                        Text(
                            "Питание",
                            color = Letify.colors.muted,
                            style = Letify.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Letify.colors.container)
                                .padding(horizontal = 14.dp),
                        ) {
                            state.meals.forEachIndexed { i, meal ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Letify.colors.bg),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        SolarIcon(name = meal.icon, tint = meal.color, size = 14.dp)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        meal.title,
                                        color = Letify.colors.text,
                                        style = Letify.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        meal.kcal?.toString() ?: "—",
                                        color = Letify.colors.muted,
                                        style = Letify.typography.bodySmall,
                                    )
                                }
                                if (i < state.meals.lastIndex) {
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(Letify.colors.track))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        // Fixed header — no dim overlay
        Box(
            Modifier
                .fillMaxWidth()
                .zIndex(30f)
                .graphicsLayer { alpha = progress.value }
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CircleHeaderBtn(onClick = onBack) {
                    BackChevron(tint = Color.White, size = 20.dp)
                }
                Spacer(Modifier.weight(1f))
                CircleHeaderBtn(onClick = {}) {
                    Text("⋯", color = Color.White, fontSize = 18.sp)
                }
            }
        }
    }
}


@Composable
private fun CircleHeaderBtn(onClick: () -> Unit, content: @Composable () -> Unit) {
    NoFeedbackButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.28f)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun DayStat(modifier: Modifier, value: String, label: String) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Letify.colors.container)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Letify.colors.text, style = Letify.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(label, color = Letify.colors.muted, style = Letify.typography.labelSmall, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun NoteEditorScreen(
    initial: String,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(40f)
            .background(Letify.colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Transparent top — only buttons
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NoFeedbackButton(onClick = onCancel) {
                    Text("Отмена", color = Letify.colors.muted, style = Letify.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
                }
                Spacer(Modifier.weight(1f))
                NoFeedbackButton(
                    onClick = { onSave(text) },
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Letify.colors.accent).padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text("Готово", color = Color.White, style = Letify.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                "Заметка",
                color = Letify.colors.muted,
                style = Letify.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            )
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = Letify.colors.text, fontSize = 18.sp, lineHeight = 27.sp),
                cursorBrush = SolidColor(Letify.colors.accent),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) {
                            Text("Что было в этот день…", color = Letify.colors.muted, fontSize = 18.sp)
                        }
                        inner()
                    }
                },
            )
            Text(
                "${text.length} символов",
                color = Letify.colors.muted,
                style = Letify.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
            )
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────


// Resolve a tapped tile's bounds into a flight source: the tile's real
// on-screen rect, unmodified — no squash, no position clamp — or null when
// the tile is mostly invisible (behind the header / off-screen), in which
// case the caller fades instead of flies. The header floats above the fly
// in its own top layer (see MomentsHeader), so the source rect never needs
// to be nudged to avoid it.
private fun flightSourceBounds(
    raw: Rect,
    screenW: Float,
    screenH: Float,
    headerBottom: Float,
): Rect? {
    if (raw.width < 8f || raw.height < 8f) return null
    // Visible slice of the tile inside the area below the header.
    val visTop = maxOf(raw.top, headerBottom)
    val visBottom = minOf(raw.bottom, screenH)
    val visLeft = maxOf(raw.left, 0f)
    val visRight = minOf(raw.right, screenW)
    val visH = visBottom - visTop
    val visW = visRight - visLeft
    if (visH < raw.height * 0.45f || visW < raw.width * 0.45f) {
        // Mostly off-screen / under the header — fade, don't fly.
        return null
    }
    // The tile's REAL on-screen rect, untouched — no size squash, no
    // position clamp. It's fine for this to start/end partly under the
    // header or past a screen edge: the header now lives in its own
    // always-on-top layer (MomentsHeader) and the photo simply passes
    // underneath it, so there's no need to shove the rect into "safe"
    // bounds anymore. Clamping used to relocate the start point away from
    // where the tile actually was, which is what read as a "jump".
    return raw
}

// A layout-phase-only interpolation from `sourceBounds` to `heroBounds`,
// driven by `progress`. Deliberately implemented with Modifier.layout
// (not a plain composable read of progress.value) so that reading the
// animation every frame only triggers remeasure of this one node — never a
// recomposition of the caller. Recomposing the whole detail screen (stats,
// tasks, meals, day strip) 60×/sec was the actual cause of the animation
// lag and the "wrong size after closing" glitch.
private fun Modifier.flightBounds(
    progress: Animatable<Float, AnimationVector1D>,
    sourceBounds: Rect?,
    heroBounds: Rect,
): Modifier = this
    .offset {
        val p = progress.value
        val src = sourceBounds
        val left = if (src != null && p < 1f) src.left + (heroBounds.left - src.left) * p else heroBounds.left
        val top = if (src != null && p < 1f) src.top + (heroBounds.top - src.top) * p else heroBounds.top
        IntOffset(left.roundToInt(), top.roundToInt())
    }
    .layout { measurable, _ ->
        val p = progress.value
        val src = sourceBounds
        val rect = if (src != null && p < 1f) {
            Rect(
                left = src.left + (heroBounds.left - src.left) * p,
                top = src.top + (heroBounds.top - src.top) * p,
                right = src.right + (heroBounds.right - src.right) * p,
                bottom = src.bottom + (heroBounds.bottom - src.bottom) * p,
            )
        } else heroBounds
        val w = rect.width.roundToInt().coerceAtLeast(1)
        val h = rect.height.roundToInt().coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(w, h))
        layout(w, h) {
            placeable.placeRelative(0, 0)
        }
    }

private fun mediaDisplayUri(item: MediaItem): String =
    item.thumbUri.ifBlank { item.uri }

private fun dateKeyOf(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate().toString()

private fun formatTime(epochMs: Long): String {
    val t = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime()
    return "%02d:%02d".format(t.hour, t.minute)
}

private fun formatDayTitle(dateKey: String): String {
    val date = runCatching { LocalDate.parse(dateKey) }.getOrNull() ?: return dateKey
    val today = LocalDate.now()
    val locale = Locale("ru")
    return when (date) {
        today -> "Сегодня, " + date.format(DateTimeFormatter.ofPattern("d MMMM", locale))
        today.minusDays(1) -> "Вчера, " + date.format(DateTimeFormatter.ofPattern("d MMMM", locale))
        else -> date.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", locale))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
}

private fun formatSleep(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "${h}ч" else "$h:${"%02d".format(m)}"
}

private fun groupByDay(items: List<MediaItem>): List<Pair<String, List<MediaItem>>> {
    val today = LocalDate.now()
    val locale = Locale("ru")
    val map = linkedMapOf<String, MutableList<MediaItem>>()
    items.sortedByDescending { it.createdAt }.forEach { item ->
        val date = Instant.ofEpochMilli(item.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val label = when (date) {
            today -> "Сегодня, " + date.format(DateTimeFormatter.ofPattern("d MMMM", locale))
            today.minusDays(1) -> "Вчера, " + date.format(DateTimeFormatter.ofPattern("d MMMM", locale))
            else -> date.format(DateTimeFormatter.ofPattern("d MMMM", locale))
        }
        map.getOrPut(label) { mutableListOf() }.add(item)
    }
    return map.map { it.key to it.value }
}

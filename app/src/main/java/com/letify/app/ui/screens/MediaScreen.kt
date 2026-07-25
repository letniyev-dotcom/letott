package com.letify.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.letify.app.ui.components.BackButton
import com.letify.app.ui.components.NoFeedbackButton
import com.letify.app.ui.icons.SolarIcon
import com.letify.app.ui.state.Dates
import com.letify.app.ui.state.LocalAppState
import com.letify.app.ui.state.MediaItem
import com.letify.app.ui.theme.Letify
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
    LaunchedEffect(Unit) { state.reloadMedia(context.filesDir) }

    var selectedId by remember { mutableStateOf<String?>(null) }
    var sourceBounds by remember { mutableStateOf<Rect?>(null) }
    var hideTileId by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    val selected = selectedId?.let { id -> state.mediaItems.find { it.id == id } }
    val dayItems = selected?.let { s ->
        state.mediaItems.filter { dateKeyOf(it.createdAt) == dateKeyOf(s.createdAt) }
    }.orEmpty()

    Box(Modifier.fillMaxSize().background(Letify.colors.bg)) {
        MomentsListScreen(
            items = state.mediaItems.toList(),
            hideTileId = hideTileId,
            onBack = onBack,
            onOpenCamera = onOpenCamera,
            onOpenItem = { item, bounds ->
                sourceBounds = bounds
                hideTileId = item.id
                selectedId = item.id
            },
        )

        if (selected != null) {
            MomentDayHost(
                item = selected,
                dayItems = dayItems,
                sourceBounds = sourceBounds,
                onSelect = { selectedId = it.id },
                onClosed = {
                    selectedId = null
                    hideTileId = null
                    sourceBounds = null
                },
                onOpenEditor = { showEditor = true },
            )
        }

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
    onBack: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenItem: (MediaItem, Rect) -> Unit,
) {
    val bg = Letify.colors.bg
    val groups = remember(items) { groupByDay(items) }
    val photoCount = items.count { !it.isVideo }
    val videoCount = items.count { it.isVideo }

    Box(Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 56.dp),
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
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 56.dp, bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalItemSpacing = 6.dp,
            ) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Text(
                        buildString {
                            if (photoCount > 0) append("$photoCount фото")
                            if (photoCount > 0 && videoCount > 0) append(" · ")
                            if (videoCount > 0) append("$videoCount видео")
                        },
                        color = Letify.colors.muted,
                        style = Letify.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
                    )
                }
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
                            onClick = { bounds -> onOpenItem(item, bounds) },
                        )
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .zIndex(2f)
                .background(Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0.92f), bg.copy(alpha = 0f))))
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 2.dp, bottom = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onClick = onBack, tint = Letify.colors.text, glyphSize = 24.dp)
                Text(
                    "Моменты",
                    color = Letify.colors.text,
                    style = Letify.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(44.dp))
            }
        }

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

@Composable
private fun MomentTile(item: MediaItem, hidden: Boolean, onClick: (Rect) -> Unit) {
    val ratio = item.aspectRatio.coerceIn(0.55f, 1.4f)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    NoFeedbackButton(
        onClick = { if (bounds != Rect.Zero) onClick(bounds) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1C1C22))
                .graphicsLayer { alpha = if (hidden) 0f else 1f },
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(item.uri).crossfade(120).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (item.isVideo) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
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

// ── Day host with fly + sheet ──────────────────────────────────────────────

@Composable
private fun MomentDayHost(
    item: MediaItem,
    dayItems: List<MediaItem>,
    sourceBounds: Rect?,
    onSelect: (MediaItem) -> Unit,
    onClosed: () -> Unit,
    onOpenEditor: () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var targetBounds by remember { mutableStateOf<Rect?>(null) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        progress.snapTo(0f)
        progress.animateTo(1f, FlySpec)
    }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            progress.animateTo(0f, FlySpec)
            onClosed()
        }
    }

    val p = progress.value
    val src = sourceBounds
    val dst = targetBounds

    Box(Modifier.fillMaxSize().zIndex(10f)) {
        // Detail screen slides up
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = (1f - p) * size.height
                    alpha = 0.3f + 0.7f * p
                }
                .background(Color(0xFF0E0E10)),
        ) {
            MomentDayContent(
                item = item,
                dayItems = dayItems,
                photoAlpha = when {
                    src == null || dst == null -> p
                    p >= 0.99f -> 1f
                    else -> 0f
                },
                onPhotoPositioned = { targetBounds = it },
                onBack = { close() },
                onSelect = onSelect,
                onOpenEditor = onOpenEditor,
            )
        }

        // Flying photo
        if (src != null && dst != null && p < 0.999f) {
            val l = src.left + (dst.left - src.left) * p
            val t = src.top + (dst.top - src.top) * p
            val w = src.width + (dst.width - src.width) * p
            val h = src.height + (dst.height - src.height) * p
            val radius = with(density) { (14.dp.toPx() + (20.dp.toPx() - 14.dp.toPx()) * p) }
            Box(
                Modifier
                    .offset { IntOffset(l.roundToInt(), t.roundToInt()) }
                    .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                    .clip(RoundedCornerShape(with(density) { radius.toDp() }))
                    .zIndex(20f),
            ) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
private fun MomentDayContent(
    item: MediaItem,
    dayItems: List<MediaItem>,
    photoAlpha: Float,
    onPhotoPositioned: (Rect) -> Unit,
    onBack: () -> Unit,
    onSelect: (MediaItem) -> Unit,
    onOpenEditor: () -> Unit,
) {
    val state = LocalAppState.current
    val dateKey = dateKeyOf(item.createdAt)
    val today = Dates.todayKey()
    val isToday = dateKey == today
    val weight = state.weightLog.find { it.dateKey == dateKey }?.kg
    val waterMl = state.waterEntriesOn(dateKey).sumOf { it.ml }
    val sleep = state.sleepLog.find { it.dateKey == dateKey }
    val tasks = state.tasksOn(dateKey)
    val config = LocalConfiguration.current
    val maxPhotoH = (config.screenHeightDp * 0.52f).dp
    val ratio = item.aspectRatio.coerceIn(0.55f, 1.6f)

    // Fixed header
    Box(Modifier.fillMaxSize()) {
        // Scroll with spacer + sheet
        val scroll = rememberScrollState()
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            // Spacer for header + photo area
            Spacer(Modifier.height(98.dp))
            // Photo
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(max = maxPhotoH)
                    .aspectRatio(ratio)
                    .onGloballyPositioned { onPhotoPositioned(it.boundsInRoot()) }
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1A1A1E))
                    .graphicsLayer { alpha = photoAlpha },
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(item.uri).crossfade(false).build(),
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

            Spacer(Modifier.height(14.dp))

            // Sheet
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(Letify.colors.container)
                    .padding(bottom = 40.dp),
            ) {
                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(99.dp)).background(Letify.colors.track))
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(formatDayTitle(dateKey), color = Letify.colors.text, style = Letify.typography.titleLarge, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(formatTime(item.createdAt), color = Letify.colors.muted, style = Letify.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp, bottom = 14.dp))

                    if (dayItems.size > 1) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            dayItems.forEach { m ->
                                val on = m.id == item.id
                                NoFeedbackButton(onClick = { onSelect(m) }) {
                                    Box(
                                        Modifier
                                            .padding(horizontal = 4.dp)
                                            .width(64.dp)
                                            .height(84.dp)
                                            .then(if (on) Modifier.border(2.5.dp, Letify.colors.accent, RoundedCornerShape(14.dp)) else Modifier)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xFF1C1C22)),
                                    ) {
                                        AsyncImage(model = m.uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    }
                                }
                            }
                        }
                    }

                    NoFeedbackButton(onClick = onOpenEditor, modifier = Modifier.fillMaxWidth()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Letify.colors.bg)
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
                    Text("Этот день", color = Letify.colors.muted, style = Letify.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DayStat(Modifier.weight(1f), weight?.let { String.format(Locale.US, "%.1f", it) } ?: "—", "кг")
                        DayStat(
                            Modifier.weight(1f),
                            if (waterMl > 0) {
                                val l = waterMl / 1000f
                                if (l >= 1f) String.format(Locale.US, "%.1f", l) else waterMl.toString()
                            } else "—",
                            if (waterMl >= 1000) "л" else "мл",
                        )
                        DayStat(Modifier.weight(1f), if (isToday && state.kcal > 0) state.kcal.toString() else "—", "ккал")
                        DayStat(Modifier.weight(1f), sleep?.let { formatSleep(it.durationMinutes) } ?: "—", "сон")
                    }

                    if (tasks.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        Text("План", color = Letify.colors.muted, style = Letify.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Letify.colors.bg).padding(horizontal = 14.dp)) {
                            tasks.forEachIndexed { i, task ->
                                val done = task.isCompletedOn(dateKey)
                                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Letify.colors.container), contentAlignment = Alignment.Center) {
                                        SolarIcon(name = task.icon, tint = task.color, size = 14.dp)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(task.name, color = Letify.colors.text, style = Letify.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                                    Text(if (done) "готово" else "—", color = if (done) Color(0xFF4ECB71) else Letify.colors.muted, style = Letify.typography.bodySmall, fontWeight = if (done) FontWeight.SemiBold else FontWeight.Normal)
                                }
                                if (i < tasks.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Letify.colors.track))
                            }
                        }
                    }

                    if (isToday) {
                        Spacer(Modifier.height(18.dp))
                        Text("Питание", color = Letify.colors.muted, style = Letify.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Letify.colors.bg).padding(horizontal = 14.dp)) {
                            state.meals.forEachIndexed { i, meal ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Letify.colors.container), contentAlignment = Alignment.Center) {
                                        SolarIcon(name = meal.icon, tint = meal.color, size = 14.dp)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(meal.title, color = Letify.colors.text, style = Letify.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text(meal.kcal?.toString() ?: "—", color = Letify.colors.muted, style = Letify.typography.bodySmall)
                                }
                                if (i < state.meals.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Letify.colors.track))
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        // Fixed header buttons
        Box(
            Modifier
                .fillMaxWidth()
                .zIndex(30f)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CircleHeaderBtn(onClick = onBack) {
                    Text("‹", color = Color.White, fontSize = 22.sp)
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
        modifier.clip(RoundedCornerShape(14.dp)).background(Letify.colors.bg).padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Letify.colors.text, style = Letify.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(label, color = Letify.colors.muted, style = Letify.typography.labelSmall, modifier = Modifier.padding(top = 3.dp))
    }
}

// ── Note editor (fullscreen, transparent top) ──────────────────────────────

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

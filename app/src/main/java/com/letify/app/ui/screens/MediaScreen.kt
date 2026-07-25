package com.letify.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import com.letify.app.ui.state.TaskStatus
import com.letify.app.ui.theme.Letify
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Moments gallery + day detail.
 * List is grouped by day. Tapping a tile opens the day card: photo, note,
 * and a snapshot of weight / water / kcal / sleep / plan / meals for that date.
 */
@Composable
fun MediaScreen(
    onBack: () -> Unit,
    onOpenCamera: () -> Unit,
) {
    val state = LocalAppState.current
    val context = LocalContext.current
    LaunchedEffect(Unit) { state.reloadMedia(context.filesDir) }

    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = selectedId?.let { id -> state.mediaItems.find { it.id == id } }

    if (selected != null) {
        MomentDayScreen(
            item = selected,
            dayItems = state.mediaItems.filter {
                dateKeyOf(it.createdAt) == dateKeyOf(selected.createdAt)
            },
            onSelect = { selectedId = it.id },
            onBack = { selectedId = null },
        )
    } else {
        MomentsListScreen(
            items = state.mediaItems.toList(),
            onBack = onBack,
            onOpenCamera = onOpenCamera,
            onOpenItem = { selectedId = it.id },
        )
    }
}

// ── List ───────────────────────────────────────────────────────────────────

@Composable
private fun MomentsListScreen(
    items: List<MediaItem>,
    onBack: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenItem: (MediaItem) -> Unit,
) {
    val bg = Letify.colors.bg
    val groups = remember(items) { groupByDay(items) }
    val photoCount = items.count { !it.isVideo }
    val videoCount = items.count { it.isVideo }

    Box(Modifier.fillMaxSize().background(bg)) {
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
                        Modifier
                            .size(64.dp)
                            .background(Letify.colors.container, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        SolarIcon(
                            name = "gallery-bold-duotone",
                            tint = Letify.colors.muted,
                            size = 28.dp,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Пока пусто",
                        color = Letify.colors.text,
                        style = Letify.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Нажми + чтобы сделать фото\nили удерживай для видео",
                        color = Letify.colors.muted,
                        style = Letify.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 56.dp,
                    bottom = 100.dp,
                ),
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
                        MomentTile(item = item, onClick = { onOpenItem(item) })
                    }
                }
            }
        }

        // Floating header
        Box(
            Modifier
                .fillMaxWidth()
                .zIndex(2f)
                .background(
                    Brush.verticalGradient(
                        listOf(bg, bg.copy(alpha = 0.92f), bg.copy(alpha = 0f)),
                    ),
                )
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 2.dp, bottom = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onClick = onBack, tint = Letify.colors.text, glyphSize = 24.dp)
                Text(
                    "Моменты",
                    color = Letify.colors.text,
                    style = Letify.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.size(44.dp))
            }
        }

        // FAB camera
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
private fun MomentTile(item: MediaItem, onClick: () -> Unit) {
    val ratio = item.aspectRatio.coerceIn(0.55f, 1.4f)
    NoFeedbackButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1C1C22)),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.uri)
                    .crossfade(180)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (item.isVideo) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.18f)),
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
            if (time.isNotEmpty()) {
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

// ── Day detail ─────────────────────────────────────────────────────────────

@Composable
private fun MomentDayScreen(
    item: MediaItem,
    dayItems: List<MediaItem>,
    onSelect: (MediaItem) -> Unit,
    onBack: () -> Unit,
) {
    val state = LocalAppState.current
    val dateKey = dateKeyOf(item.createdAt)
    val today = Dates.todayKey()

    val weight = state.weightLog.find { it.dateKey == dateKey }?.kg
    val waterMl = state.waterEntriesOn(dateKey).sumOf { it.ml }
    val sleep = state.sleepLog.find { it.dateKey == dateKey }
    val tasks = state.tasksOn(dateKey)
    val isToday = dateKey == today

    var editingNote by remember(item.id) { mutableStateOf(false) }
    var noteDraft by remember(item.id) { mutableStateOf(item.note) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Letify.colors.bg)
            .verticalScroll(rememberScrollState()),
    ) {
        // Hero
        Box(
            Modifier
                .fillMaxWidth()
                .height(380.dp)
                .background(Color(0xFF121218)),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.uri)
                    .crossfade(180)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
                        ),
                    ),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onClick = onBack, tint = Color.White, glyphSize = 24.dp)
                Spacer(Modifier.weight(1f))
            }
            if (item.isVideo) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("▶", color = Color.White, fontSize = 36.sp)
                }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                formatDayTitle(dateKey),
                color = Letify.colors.text,
                style = Letify.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                formatTime(item.createdAt),
                color = Letify.colors.muted,
                style = Letify.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
            )

            // Strip of other photos that day
            if (dayItems.size > 1) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    dayItems.forEach { m ->
                        val on = m.id == item.id
                        NoFeedbackButton(onClick = { onSelect(m) }) {
                            Box(
                                Modifier
                                    .width(56.dp)
                                    .height(74.dp)
                                    .then(
                                        if (on) Modifier.border(
                                            2.dp,
                                            Letify.colors.accent,
                                            RoundedCornerShape(10.dp),
                                        ) else Modifier
                                    )
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1C1C22)),
                            ) {
                                AsyncImage(
                                    model = m.uri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
            }

            // Note
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Letify.colors.container)
                    .padding(14.dp),
            ) {
                Column {
                    if (editingNote) {
                        BasicTextField(
                            value = noteDraft,
                            onValueChange = { noteDraft = it },
                            textStyle = TextStyle(
                                color = Letify.colors.text,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                            ),
                            cursorBrush = SolidColor(Letify.colors.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp),
                            decorationBox = { inner ->
                                Box {
                                    if (noteDraft.isEmpty()) {
                                        Text(
                                            "Добавь заметку к этому дню…",
                                            color = Letify.colors.muted,
                                            style = Letify.typography.bodyMedium,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NoFeedbackButton(onClick = {
                                state.setMediaNote(item.id, noteDraft)
                                editingNote = false
                            }) {
                                Text(
                                    "Сохранить",
                                    color = Letify.colors.accent,
                                    style = Letify.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            NoFeedbackButton(onClick = {
                                noteDraft = item.note
                                editingNote = false
                            }) {
                                Text(
                                    "Отмена",
                                    color = Letify.colors.muted,
                                    style = Letify.typography.bodyMedium,
                                )
                            }
                        }
                    } else {
                        Text(
                            item.note.ifBlank { "Нет заметки" },
                            color = if (item.note.isBlank()) Letify.colors.muted else Letify.colors.text,
                            style = Letify.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        NoFeedbackButton(onClick = {
                            noteDraft = item.note
                            editingNote = true
                        }) {
                            Text(
                                if (item.note.isBlank()) "Добавить" else "Изменить",
                                color = Letify.colors.accent,
                                style = Letify.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "Этот день",
                color = Letify.colors.muted,
                style = Letify.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DayStat(
                    modifier = Modifier.weight(1f),
                    value = weight?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                    label = "кг",
                )
                DayStat(
                    modifier = Modifier.weight(1f),
                    value = if (waterMl > 0) {
                        val l = waterMl / 1000f
                        if (l >= 1f) String.format(Locale.US, "%.1f", l) else waterMl.toString()
                    } else "—",
                    label = if (waterMl > 0 && waterMl >= 1000) "л" else "мл",
                )
                DayStat(
                    modifier = Modifier.weight(1f),
                    value = if (isToday && state.kcal > 0) state.kcal.toString() else "—",
                    label = "ккал",
                )
                DayStat(
                    modifier = Modifier.weight(1f),
                    value = sleep?.let { formatSleep(it.durationMinutes) } ?: "—",
                    label = "сон",
                )
            }

            if (tasks.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "План",
                    color = Letify.colors.muted,
                    style = Letify.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Letify.colors.container)
                        .padding(horizontal = 14.dp),
                ) {
                    tasks.forEachIndexed { i, task ->
                        val done = task.isCompletedOn(dateKey)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(26.dp)
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
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Letify.colors.track),
                            )
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
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Letify.colors.container)
                        .padding(horizontal = 14.dp),
                ) {
                    state.meals.forEachIndexed { i, meal ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(26.dp)
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
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Letify.colors.track),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
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
        Text(
            value,
            color = Letify.colors.text,
            style = Letify.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            label,
            color = Letify.colors.muted,
            style = Letify.typography.labelSmall,
            modifier = Modifier.padding(top = 3.dp),
        )
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

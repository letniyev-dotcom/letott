@file:OptIn(
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.letify.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.letify.app.ui.components.BackChevron
import com.letify.app.ui.components.NoFeedbackButton
import com.letify.app.ui.components.noFeedbackClick
import com.letify.app.ui.icons.SolarIcon
import com.letify.app.ui.state.AppState
import com.letify.app.ui.state.LocalAppState
import com.letify.app.ui.state.MediaItem
import com.letify.app.ui.state.VoiceNote
import com.letify.app.ui.state.WallNote
import com.letify.app.ui.theme.Letify
import com.letify.app.ui.theme.LetifyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/**
 * "Стена" — replaces the old photo-only "Моменты" gallery. A single feed of
 * message-style blocks (photo/video, voice note, text note), rendered like
 * outgoing chat bubbles (Telegram "Saved Messages" style — this is a wall
 * you write to yourself). Backed entirely by state already scaffolded in
 * AppState: [AppState.mediaItems] / [AppState.voiceNotes] / [AppState.wallNotes].
 *
 * Two sub-screens (feed / profile) live in ONE composable so the avatar and
 * title can genuinely fly between them via [SharedTransitionLayout] — no
 * need to touch the app-level overlay stack or the bespoke Home⇄Profile
 * flight system for this.
 */
@Composable
fun WallScreen(
    onBack: () -> Unit,
    onOpenCamera: () -> Unit,
) {
    val state = LocalAppState.current
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (!state.mediaLoaded) state.ensureMediaLoaded(context.filesDir)
    }

    var showProfile by remember { mutableStateOf(false) }
    BackHandler(enabled = showProfile) { showProfile = false }

    SharedTransitionLayout(Modifier.fillMaxSize().background(Letify.colors.bg)) {
        AnimatedContent(
            targetState = showProfile,
            transitionSpec = {
                if (targetState) {
                    // Opening the profile: it grows in a touch slower than the
                    // feed dismisses, so the flight reads as a reveal.
                    fadeIn(tween(240)) togetherWith fadeOut(tween(160))
                } else {
                    // Closing: the exact mirror of opening — same 240ms/160ms
                    // pairing, just re-assigned so Profile's fade-out matches
                    // the duration it faded in with, instead of feeling abrupt.
                    fadeIn(tween(160)) togetherWith fadeOut(tween(240))
                }
            },
            label = "WallHost",
        ) { profileVisible ->
            if (profileVisible) {
                WallProfileContent(
                    sharedScope = this@SharedTransitionLayout,
                    animatedScope = this@AnimatedContent,
                    state = state,
                    onBack = { showProfile = false },
                )
            } else {
                WallFeedContent(
                    sharedScope = this@SharedTransitionLayout,
                    animatedScope = this@AnimatedContent,
                    state = state,
                    onOpenProfile = { showProfile = true },
                    onOpenCamera = onOpenCamera,
                    onBack = onBack,
                )
            }
        }
    }
}

// ─────────────────────────── Feed blocks / helpers ────────────────────────

private sealed class WallBlock(val createdAt: Long, val key: String) {
    class Photo(val item: MediaItem) : WallBlock(item.createdAt, "media_${item.id}")
    class Voice(val item: VoiceNote) : WallBlock(item.createdAt, "voice_${item.id}")
    class Note(val item: WallNote) : WallBlock(item.createdAt, "note_${item.id}")
}

private sealed class FeedRow {
    class Header(val label: String) : FeedRow()
    class Item(val block: WallBlock) : FeedRow()
}

private fun buildBlocks(state: AppState): List<WallBlock> =
    (state.mediaItems.map { WallBlock.Photo(it) } +
        state.voiceNotes.map { WallBlock.Voice(it) } +
        state.wallNotes.map { WallBlock.Note(it) })
        .sortedByDescending { it.createdAt }

/**
 * [blocks] is newest-first. The feed renders with `reverseLayout = true`
 * (index 0 pinned to the bottom of the screen, like Telegram/any real
 * messenger — new messages appear at the bottom, not the top) so a day's
 * header must come AFTER that day's items in this list, not before: with
 * reverseLayout, later indices render higher on screen, so putting the
 * header right after the day's oldest item is what makes it visually land
 * above that day's messages instead of underneath them.
 */
private fun buildRows(blocks: List<WallBlock>): List<FeedRow> {
    val rows = mutableListOf<FeedRow>()
    var i = 0
    while (i < blocks.size) {
        val label = dayLabel(blocks[i].createdAt)
        while (i < blocks.size && dayLabel(blocks[i].createdAt) == label) {
            rows += FeedRow.Item(blocks[i])
            i++
        }
        rows += FeedRow.Header(label)
    }
    return rows
}

private fun dayLabel(epochMs: Long): String {
    val date = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> date.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru")))
    }
}

private fun timeLabel(epochMs: Long): String {
    val zdt = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    return "%02d:%02d".format(zdt.hour, zdt.minute)
}

private fun durationLabel(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

private fun pluralMessages(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    val word = when {
        mod100 in 11..14 -> "сообщений"
        mod10 == 1 -> "сообщение"
        mod10 in 2..4 -> "сообщения"
        else -> "сообщений"
    }
    return "$n $word"
}

// ─────────────────────────── Shared "wall identity" ───────────────────────

/**
 * Small gradient glyph standing in for the wall itself (not the user).
 *
 * The feed<->profile flight is driven by `sharedBounds` + ScaleToBounds
 * on the call sites (not by this composable) — that's what keeps the
 * inner icon from popping between the 38dp feed size and the 88dp
 * profile size. [BoxWithConstraints] here just keeps the icon sized
 * proportionally (50% of whatever width it's actually given) instead of
 * hardcoding it off the `size` param twice.
 */
@Composable
private fun WallAvatar(size: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Letify.colors.accent, LetifyColors.TileSky),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        SolarIcon(name = "notebook-bold-duotone", tint = Color.White, size = maxWidth * 0.5f)
    }
}

// ─────────────────────────── Glass island container ───────────────────────

@Composable
private fun GlassIsland(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(26.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(
                if (Letify.colors.isDark) Color(0xE6181818) else Color(0xF2FFFFFF),
                shape,
            ),
    ) { content() }
}

// ─────────────────────────── FEED ──────────────────────────────────────────

@Composable
private fun WallFeedContent(
    sharedScope: androidx.compose.animation.SharedTransitionScope,
    animatedScope: androidx.compose.animation.AnimatedContentScope,
    state: AppState,
    onOpenProfile: () -> Unit,
    onOpenCamera: () -> Unit,
    onBack: () -> Unit,
) = with(sharedScope) {
    val context = LocalContext.current
    val blocks = remember(state.mediaItems.size, state.voiceNotes.size, state.wallNotes.size) {
        buildBlocks(state)
    }
    val rows = remember(blocks) { buildRows(blocks) }

    var playingId by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { player?.release() } }

    fun togglePlay(voiceItem: VoiceNote) {
        val current = player
        if (playingId == voiceItem.id) {
            current?.stop(); current?.release()
            player = null; playingId = null
            return
        }
        current?.stop(); current?.release()
        val mp = MediaPlayer()
        runCatching {
            mp.setDataSource(context, android.net.Uri.parse(voiceItem.uri))
            mp.setOnCompletionListener { playingId = null }
            mp.prepare()
            mp.start()
        }
        player = mp
        playingId = voiceItem.id
    }

    // ── voice recording ────────────────────────────────────────────────
    var isRecording by remember { mutableStateOf(false) }
    var recSeconds by remember { mutableIntStateOf(0) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordPath by remember { mutableStateOf<String?>(null) }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    fun beginRecording() {
        val dir = File(context.filesDir, "voice").apply { mkdirs() }
        val file = File(dir, "voice_${System.currentTimeMillis()}.m4a")
        val rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        val ok = runCatching {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(96_000)
            rec.setAudioSamplingRate(44_100)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
        }.isSuccess
        if (!ok) { runCatching { rec.release() }; return }
        recorder = rec
        recordPath = file.absolutePath
        recSeconds = 0
        isRecording = true
    }

    fun stopRecording(save: Boolean) {
        val rec = recorder
        val path = recordPath
        isRecording = false
        recorder = null
        recordPath = null
        if (rec != null) { runCatching { rec.stop() }; runCatching { rec.release() } }
        if (save && path != null && recSeconds > 0) {
            state.addVoiceNote(path, recSeconds * 1000)
        } else if (path != null) {
            runCatching { File(path).delete() }
        }
    }

    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasAudioPermission = granted
        if (granted) beginRecording()
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1000)
            recSeconds++
        }
    }
    DisposableEffect(Unit) { onDispose { if (isRecording) stopRecording(save = false) } }

    var noteText by remember { mutableStateOf(TextFieldValue("")) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun sendNote() {
        val text = noteText.text.trim()
        if (text.isEmpty()) return
        state.addWallNote(text)
        noteText = TextFieldValue("")
        scope.launch { listState.animateScrollToItem(0) }
    }

    // imePadding lives on the OUTER container so the message list and the
    // input pill rise together as one unit when the keyboard opens — this
    // is what makes it behave like Telegram instead of only the input bar
    // sliding up while the chat stays put underneath the keyboard.
    Box(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            reverseLayout = true,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                // Swapped vs a normal (non-reversed) list: with
                // reverseLayout=true, "top" padding sits just above index 0
                // — which reverseLayout pins to the BOTTOM of the screen,
                // right above the input pill — and "bottom" padding lands
                // near the top of the screen, under the header pill.
                top = 92.dp,
                start = 14.dp,
                end = 14.dp,
                bottom = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 78.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (rows.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Стена пока пустая",
                            color = Letify.colors.text,
                            style = Letify.typography.titleMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Сфотографируй момент, запиши голосовое\nили просто напиши, что на уме",
                            color = Letify.colors.muted,
                            style = Letify.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
            items(rows.size, key = { i -> when (val r = rows[i]) {
                is FeedRow.Header -> "hdr_${r.label}_$i"
                is FeedRow.Item -> r.block.key
            } }) { i ->
                when (val row = rows[i]) {
                    is FeedRow.Header -> DayDivider(row.label)
                    is FeedRow.Item -> WallBlockBubble(
                        block = row.block,
                        isPlaying = (row.block as? WallBlock.Voice)?.item?.id == playingId,
                        onTogglePlay = { (row.block as? WallBlock.Voice)?.let { togglePlay(it.item) } },
                        onDelete = {
                            when (val b = row.block) {
                                is WallBlock.Photo -> state.removeMedia(b.item)
                                is WallBlock.Voice -> state.removeVoiceNote(b.item)
                                is WallBlock.Note -> state.removeWallNote(b.item)
                            }
                        },
                    )
                }
            }
        }

        // ── top island ───────────────────────────────────────────────
        //   heightIn(min=...) gives it a real, fixed "thickness" that has
        //   nothing to do with the avatar's size — it won't shrink back
        //   down to just wrapping the avatar however small the avatar is.
        GlassIsland(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp)
                .wrapContentWidth()
                .heightIn(min = 92.dp)
                .noFeedbackClick(onClick = onOpenProfile)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            shape = RoundedCornerShape(30.dp),
        ) {
            Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.sharedBounds(
                        rememberSharedContentState(key = "wallAvatar"),
                        animatedVisibilityScope = animatedScope,
                        resizeMode = androidx.compose.animation.SharedTransitionScope.ResizeMode.ScaleToBounds(),
                    ),
                ) { WallAvatar(size = 38.dp) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Стена",
                        color = Letify.colors.text,
                        style = Letify.typography.titleLarge,
                        fontSize = androidx.compose.ui.unit.TextUnit(
                            19f, androidx.compose.ui.unit.TextUnitType.Sp,
                        ),
                        maxLines = 1,
                        modifier = Modifier
                            .wrapContentWidth()
                            .sharedBounds(
                                rememberSharedContentState(key = "wallTitle"),
                                animatedVisibilityScope = animatedScope,
                                resizeMode = androidx.compose.animation.SharedTransitionScope.ResizeMode.ScaleToBounds(),
                            ),
                    )
                    Text(
                        pluralMessages(state.wallEntryCount),
                        color = Letify.colors.muted,
                        style = Letify.typography.labelSmall,
                    )
                }
                Spacer(Modifier.width(6.dp))
                SolarIcon(name = "alt-arrow-down-bold-duotone", tint = Letify.colors.muted, size = 16.dp)
            }
        }

        // ── bottom input island ─────────────────────────────────────
        //   Keyboard-avoidance now lives on the parent Box (see its
        //   .imePadding() above), which lifts this pill AND the message
        //   list together; navigationBarsPadding still keeps it clear of
        //   the gesture pill once the keyboard is closed.
        GlassIsland(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 16.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isRecording) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF5A5A)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "%d:%02d".format(recSeconds / 60, recSeconds % 60),
                        color = Letify.colors.text,
                        style = Letify.typography.labelLarge,
                    )
                    Spacer(Modifier.width(10.dp))
                    RecordingWave(Modifier.weight(1f))
                    IslandButton(icon = "close-circle-bold-duotone", tint = Color(0xFFFF7A7A)) {
                        stopRecording(save = false)
                    }
                    Spacer(Modifier.width(6.dp))
                    IslandButton(
                        icon = "check-bold",
                        tint = Color.White,
                        background = Brush.linearGradient(listOf(LetifyColors.Mint, LetifyColors.TileTeal)),
                    ) { stopRecording(save = true) }
                } else {
                    Box(Modifier.weight(1f).padding(start = 12.dp)) {
                        BasicTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Letify.colors.text,
                                fontSize = androidx.compose.ui.unit.TextUnit(
                                    14.5f, androidx.compose.ui.unit.TextUnitType.Sp,
                                ),
                            ),
                            cursorBrush = SolidColor(Letify.colors.accent),
                            decorationBox = { inner ->
                                if (noteText.text.isEmpty()) {
                                    Text(
                                        "Заметка на стене…",
                                        color = Letify.colors.muted,
                                        style = Letify.typography.bodyMedium,
                                    )
                                }
                                inner()
                            },
                        )
                    }
                    if (noteText.text.isNotBlank()) {
                        IslandButton(
                            icon = "alt-arrow-right-bold",
                            tint = Color.White,
                            background = Brush.linearGradient(listOf(Letify.colors.accent, LetifyColors.TileViolet)),
                        ) { sendNote() }
                    } else {
                        NoFeedbackButton(
                            onClick = {
                                if (hasAudioPermission) beginRecording()
                                else audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Letify.colors.track),
                                contentAlignment = Alignment.Center,
                            ) { SolarIcon(name = "microphone-bold-duotone", tint = Letify.colors.text, size = 18.dp) }
                        }
                        Spacer(Modifier.width(6.dp))
                        IslandButton(
                            icon = "camera-bold-duotone",
                            tint = Color.White,
                            background = Brush.linearGradient(listOf(Letify.colors.accent, LetifyColors.TileViolet)),
                        ) { onOpenCamera() }
                    }
                }
            }
        }
    }
}

/** Small circular icon button matching the island chrome. */
@Composable
private fun IslandButton(
    icon: String,
    tint: Color,
    background: Brush? = null,
    onClick: () -> Unit,
) {
    NoFeedbackButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(
                    if (background != null) Modifier.background(background)
                    else Modifier.background(Letify.colors.track),
                ),
            contentAlignment = Alignment.Center,
        ) {
            SolarIcon(name = icon, tint = tint, size = 18.dp)
        }
    }
}

@Composable
private fun RecordingWave(modifier: Modifier = Modifier) {
    val bars = remember { List(24) { Random.nextFloat() } }
    Row(modifier.height(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        bars.forEach { h ->
            Box(
                Modifier
                    .width(2.5.dp)
                    .height((6 + h * 14).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Letify.colors.accent),
            )
        }
    }
}

@Composable
private fun DayDivider(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Letify.colors.track)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text(label, color = Letify.colors.muted, style = Letify.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BubbleRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(Modifier.widthIn(max = 300.dp)) { content() }
    }
}

private val BubbleShape = RoundedCornerShape(
    topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 5.dp,
)

@Composable
private fun WallBlockBubble(
    block: WallBlock,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onDelete: () -> Unit,
) {
    when (block) {
        is WallBlock.Note -> BubbleRow {
            Column(
                Modifier
                    .clip(BubbleShape)
                    .background(Letify.colors.accentSoft)
                    .combinedClickable(onClick = {}, onLongClick = onDelete)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(block.item.text, color = Letify.colors.text, style = Letify.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    timeLabel(block.item.createdAt),
                    color = Letify.colors.muted,
                    style = Letify.typography.labelSmall,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
        is WallBlock.Voice -> BubbleRow {
            Row(
                Modifier
                    .clip(BubbleShape)
                    .background(Letify.colors.container)
                    .combinedClickable(onClick = onTogglePlay, onLongClick = onDelete)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Letify.colors.accent, LetifyColors.TileViolet))),
                    contentAlignment = Alignment.Center,
                ) {
                    SolarIcon(
                        name = if (isPlaying) "pause-bold" else "play-bold",
                        tint = Color.White,
                        size = 14.dp,
                    )
                }
                Spacer(Modifier.width(10.dp))
                StaticWave(seed = block.item.id, modifier = Modifier.width(90.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        durationLabel(block.item.durationMs),
                        color = Letify.colors.text,
                        style = Letify.typography.labelLarge,
                    )
                    Text(
                        timeLabel(block.item.createdAt),
                        color = Letify.colors.muted,
                        style = Letify.typography.labelSmall,
                    )
                }
            }
        }
        is WallBlock.Photo -> BubbleRow {
            Column(
                Modifier
                    .clip(BubbleShape)
                    .background(Letify.colors.container)
                    .combinedClickable(onClick = {}, onLongClick = onDelete),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(block.item.thumbUri.ifBlank { block.item.uri })
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(block.item.aspectRatio)
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        block.item.note.ifBlank { if (block.item.isVideo) "Видео" else "Фото" },
                        color = Letify.colors.text,
                        style = Letify.typography.bodyMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(timeLabel(block.item.createdAt), color = Letify.colors.muted, style = Letify.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StaticWave(seed: String, modifier: Modifier = Modifier) {
    val bars = remember(seed) {
        val rnd = Random(seed.hashCode())
        List(20) { 0.25f + rnd.nextFloat() * 0.75f }
    }
    Row(modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        bars.forEach { h ->
            Box(
                Modifier
                    .width(2.dp)
                    .height((5 + h * 15).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Letify.colors.muted.copy(alpha = 0.55f)),
            )
        }
    }
}

// ─────────────────────────── PROFILE ───────────────────────────────────────

private enum class WallTab { Moments, Voice, Messages }

@Composable
private fun WallProfileContent(
    sharedScope: androidx.compose.animation.SharedTransitionScope,
    animatedScope: androidx.compose.animation.AnimatedContentScope,
    state: AppState,
    onBack: () -> Unit,
) = with(sharedScope) {
    var tab by remember { mutableStateOf(WallTab.Moments) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                bottom = 40.dp,
            ),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NoFeedbackButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Letify.colors.container),
                            contentAlignment = Alignment.Center,
                        ) { BackChevron(tint = Letify.colors.text, size = 16.dp) }
                    }
                    Spacer(Modifier.width(1.dp))
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.sharedBounds(
                            rememberSharedContentState(key = "wallAvatar"),
                            animatedVisibilityScope = animatedScope,
                            resizeMode = androidx.compose.animation.SharedTransitionScope.ResizeMode.ScaleToBounds(),
                        ),
                    ) { WallAvatar(size = 88.dp) }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Стена",
                        color = Letify.colors.text,
                        style = Letify.typography.headlineLarge,
                        maxLines = 1,
                        modifier = Modifier
                            .wrapContentWidth()
                            .sharedBounds(
                                rememberSharedContentState(key = "wallTitle"),
                                animatedVisibilityScope = animatedScope,
                                resizeMode = androidx.compose.animation.SharedTransitionScope.ResizeMode.ScaleToBounds(),
                            ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        pluralMessages(state.wallEntryCount),
                        color = Letify.colors.muted,
                        style = Letify.typography.bodyMedium,
                    )
                }
            }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 18.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Letify.colors.container)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    Text(
                        "О СТЕНЕ",
                        color = Letify.colors.muted,
                        style = Letify.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Место для сохранения важных моментов — фото, голосовые заметки и мысли. Маленький архив твоих дней, который потом приятно пролистать.",
                        color = Letify.colors.text,
                        style = Letify.typography.bodyMedium,
                    )
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    GlassIsland(shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.padding(4.dp)) {
                            WallTabButton("Моменты", state.mediaItems.size, tab == WallTab.Moments) { tab = WallTab.Moments }
                            WallTabButton("Голосовые", state.voiceNotes.size, tab == WallTab.Voice) { tab = WallTab.Voice }
                            WallTabButton("Сообщения", state.wallNotes.size, tab == WallTab.Messages) { tab = WallTab.Messages }
                        }
                    }
                }
            }
            when (tab) {
                WallTab.Moments -> {
                    val photos = state.mediaItems
                    if (photos.isEmpty()) {
                        item { EmptyTabHint("Пока нет фото и видео") }
                    } else {
                        val rowsCount = (photos.size + 1) / 2
                        items(rowsCount) { r ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                for (c in 0..1) {
                                    val idx = r * 2 + c
                                    if (idx < photos.size) {
                                        val m = photos[idx]
                                        AsyncImage(
                                            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                .data(m.thumbUri.ifBlank { m.uri })
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(16.dp)),
                                        )
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
                WallTab.Voice -> {
                    val voices = state.voiceNotes
                    if (voices.isEmpty()) {
                        item { EmptyTabHint("Пока нет голосовых") }
                    } else {
                        items(voices.size) { i ->
                            val v = voices[i]
                            ProfileListRow(
                                icon = "microphone-bold-duotone",
                                title = "Голосовая заметка",
                                subtitle = "${dayLabel(v.createdAt)} · ${timeLabel(v.createdAt)}",
                                trailing = durationLabel(v.durationMs),
                            )
                        }
                    }
                }
                WallTab.Messages -> {
                    val notes = state.wallNotes
                    if (notes.isEmpty()) {
                        item { EmptyTabHint("Пока нет заметок") }
                    } else {
                        items(notes.size) { i ->
                            val n = notes[i]
                            ProfileListRow(
                                icon = "pen-bold",
                                title = n.text,
                                subtitle = "${dayLabel(n.createdAt)} · ${timeLabel(n.createdAt)}",
                                trailing = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTabHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Letify.colors.muted, style = Letify.typography.bodyMedium)
    }
}

@Composable
private fun ProfileListRow(icon: String, title: String, subtitle: String, trailing: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Letify.colors.container)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Letify.colors.accent, LetifyColors.TileViolet))),
            contentAlignment = Alignment.Center,
        ) { SolarIcon(name = icon, tint = Color.White, size = 15.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Letify.colors.text,
                style = Letify.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Letify.colors.muted, style = Letify.typography.labelSmall)
        }
        if (trailing != null) {
            Text(trailing, color = Letify.colors.muted, style = Letify.typography.labelMedium)
        }
    }
}

@Composable
private fun WallTabButton(label: String, count: Int, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) Color.White else Color.Transparent
    val fg = if (active) Color.Black else Letify.colors.muted
    NoFeedbackButton(onClick = onClick) {
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = fg, style = Letify.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            Text("$count", color = fg.copy(alpha = 0.65f), style = Letify.typography.labelSmall)
        }
    }
}

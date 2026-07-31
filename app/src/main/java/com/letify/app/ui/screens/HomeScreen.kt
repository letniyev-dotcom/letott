package com.letify.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.letify.app.ui.components.ElasticOverscroll
import com.letify.app.ui.components.NoFeedbackButton
import com.letify.app.ui.icons.SolarIcon
import com.letify.app.ui.state.LocalAppState
import com.letify.app.ui.state.MediaItem
import com.letify.app.ui.state.TaskItem
import com.letify.app.ui.state.TaskStatus
import com.letify.app.ui.theme.Letify
import com.letify.app.ui.theme.LetifyColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(
    onAddWeight: () -> Unit = {},
    onOpenNutrition: () -> Unit = {},
    onAddSleep: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onAddMeal: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenPlan: () -> Unit = {},
    onOpenMoments: () -> Unit = {},
    // Hero-style avatar/name flight (see LetifyApp.AvatarFlightOverlay): while a
    // flight is in progress the real header glyphs are hidden (alpha 0, still
    // laid out) and a single overlay element flies in their place instead.
    // Stable State, not a plain Boolean: read ONLY inside graphicsLayer{}
    // (draw phase) below. A plain Boolean here used to force a full
    // HomeScreen recomposition (every task/date/progress recalculated)
    // at the exact moment the flight starts and again when it ends —
    // that recompute burst was the "рывок в начале и в конце".
    hideAvatarName: State<Boolean> = remember { mutableStateOf(false) },
    onAvatarBoundsChanged: (Rect) -> Unit = {},
    onNameBoundsChanged: (Rect) -> Unit = {},
    // The 4 metric-card stickers (sandwich/coke/cat/weight-hand) are
    // IterateForever Lottie animations — by default they keep evaluating and
    // invalidating their own layer every frame FOREVER, including while a
    // tab-switch or the Home⇄Profile hero flight is actively animating on
    // top of/away from this screen. That constant background invalidation
    // was competing for frame budget with the hero flight's own
    // graphicsLayer transform every frame, which is what read as
    // "дёргается" (stutter) during the flight even after the avatar-image
    // flicker was fixed. Default true (keep the old always-on look at
    // rest); the caller passes false only while a transition touching this
    // screen is in flight, so the stickers freeze on their current frame
    // for that ~500ms and resume the instant motion settles — imperceptible
    // as a freeze, but frees up every frame of the flight for the animation
    // that actually needs to be smooth.
    //
    // IMPORTANT: this is a stable State, not a plain Boolean, and HomeScreen
    // must forward the object AS-IS below (never read .value itself) — same
    // reasoning as hideAvatarName above. If HomeScreen read .value here to
    // decide what to pass down, that read would sit in HomeScreen's own
    // recompose scope and we'd be back to a full HomeScreen recompose on
    // every tab switch, not just the 4 small MetricCards reacting.
    animationsActive: State<Boolean> = remember { mutableStateOf(true) },
) {
    val state = LocalAppState.current
    val context = LocalContext.current
    val overall = state.overallProgress()
    val name = state.userName.ifBlank { "друг" }
    val photoUrl = state.telegramUser?.photoUrl
    val letter = name.firstOrNull()?.uppercase() ?: "?"

    val today = LocalDate.now()
    val dateShort = buildString {
        append(today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru")))
        append(", ")
        append(today.dayOfMonth)
        append(" ")
        append(today.month.getDisplayName(TextStyle.FULL, Locale("ru")))
    }

    val waterProgress = (state.waterMl.toFloat() / state.waterTarget.coerceAtLeast(1)).coerceIn(0f, 1f)
    val foodProgress = (state.kcal.toFloat() / state.kcalTarget.coerceAtLeast(1)).coerceIn(0f, 1f)

    val sandwich by rememberLottieComposition(LottieCompositionSpec.Asset("stickers/sandwich.json"))
    val coke by rememberLottieComposition(LottieCompositionSpec.Asset("stickers/coke.json"))
    val sleepCat by rememberLottieComposition(LottieCompositionSpec.Asset("stickers/sleep_cat.json"))
    val weightHand by rememberLottieComposition(LottieCompositionSpec.Asset("stickers/weight_hand.json"))
    val pagerState = rememberPagerState(pageCount = { 2 })
    val metricsScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }

    val moments = state.mediaItems.take(3)
    val tasks = state.tasksToday()
    val nowMin = LocalTime.now().toSecondOfDay() / 60
    val dateKey = com.letify.app.ui.state.Dates.todayKey()
    val done = tasks.count { it.isCompletedOn(dateKey) }

    // Plan preview: the task currently live (or, if none, the nearest upcoming
    // one) shown first, followed by the next two upcoming tasks.
    val liveTask = tasks.firstOrNull { it.statusAt(nowMin, dateKey) == TaskStatus.Live }
    val upcomingSorted = tasks
        .filter { it.statusAt(nowMin, dateKey) == TaskStatus.Upcoming }
        .sortedBy { it.startMinutes }
    val current = liveTask ?: upcomingSorted.firstOrNull()
    val nextTwo = upcomingSorted.filter { it.id != current?.id }.take(2)
    val planItems = listOfNotNull(current) + nextTwo

    Column(
        Modifier
            .fillMaxSize()
            .background(Letify.colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 6.dp, bottom = 24.dp),
    ) {
        // Header → profile. feedback = false: this button spans the whole row
        // (avatar + name + date on the right) so onOpenProfile also fires from
        // a tap on the date — that's intentional (bigger target). But the
        // default press "squat" (see NoFeedbackButton) scales the WHOLE row,
        // including the date, which reads as the date itself getting pressed
        // even though only the avatar/name were tapped. Turning feedback off
        // keeps the tap target and navigation, just drops the visual squat.
        NoFeedbackButton(
            onClick = onOpenProfile,
            modifier = Modifier.fillMaxWidth(),
            feedback = false,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .onGloballyPositioned { onAvatarBoundsChanged(it.boundsInRoot()) }
                        .graphicsLayer { alpha = if (hideAvatarName.value) 0f else 1f }
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Letify.colors.accent, LetifyColors.TilePink)),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(letter, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (!photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(photoUrl).crossfade(180).build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                // Name is wrap-content (NOT weight) so boundsInWindow matches the
                // actual glyph box — otherwise the hero flight starts from a
                // full-row-wide rect and the name appears to fly in from nowhere.
                Text(
                    name,
                    color = Letify.colors.text,
                    style = Letify.typography.titleLarge,
                    fontSize = 19.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .onGloballyPositioned { onNameBoundsChanged(it.boundsInRoot()) }
                        .graphicsLayer { alpha = if (hideAvatarName.value) 0f else 1f },
                    textAlign = TextAlign.Start,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    dateShort.replaceFirstChar { it.titlecase(Locale("ru")) },
                    color = Letify.colors.muted,
                    style = Letify.typography.bodyMedium,
                )
            }
        }

        // Progress carousel — concept ideal: 152 ring, air under dots before cards.
        Box(Modifier.fillMaxWidth().height(210.dp).padding(top = 8.dp)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (page == 0) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            DayRing(overall, 152.dp)
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "прогресс за день",
                                color = Letify.colors.muted,
                                style = Letify.typography.bodyMedium,
                                maxLines = 1,
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        ) {
                            Text(
                                "ИИ · сегодня",
                                color = Letify.colors.accent,
                                style = Letify.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(Letify.colors.accent.copy(alpha = 0.14f))
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "Выпей воды — до цели осталось пол-литра. После ужина лучше лёгкая прогулка.",
                                color = Letify.colors.text,
                                style = Letify.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "советы обновляются вечером",
                                color = Letify.colors.muted,
                                style = Letify.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        // Animated page dots — active stretches into a pill (concept).
        // 10 under caption zone, 22 before first card — cards no longer glued to ring.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(2) { i ->
                val on = pagerState.currentPage == i
                val w by animateDpAsState(
                    targetValue = if (on) 18.dp else 6.dp,
                    animationSpec = tween(durationMillis = 320),
                    label = "homeDot",
                )
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(w)
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            if (on) Letify.colors.accent
                            else Letify.colors.track,
                        ),
                )
            }
        }

        // Moments — uniform 12dp gap to the next card.
        NoFeedbackButton(
            onClick = onOpenMoments,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Letify.colors.container)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MomentsStack(moments)
                Spacer(Modifier.width(12.dp))
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Стена", color = Letify.colors.text, style = Letify.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.wallEntryCount == 0) "добавь первую запись"
                        else "${state.wallEntryCount} в ленте",
                        color = Letify.colors.muted,
                        style = Letify.typography.bodyMedium,
                    )
                }
                Text("›", color = Letify.colors.muted, style = Letify.typography.titleMedium)
            }
        }

        // Plan — compact card: current task + next two, small scale
        NoFeedbackButton(
            onClick = onOpenPlan,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Letify.colors.container)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "План",
                        color = Letify.colors.text,
                        style = Letify.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "$done / ${tasks.size}",
                        color = Letify.colors.accent,
                        style = Letify.typography.labelMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(Letify.colors.accent.copy(alpha = 0.14f))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
                if (planItems.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("нет задач", color = Letify.colors.muted, style = Letify.typography.bodyMedium)
                } else {
                    Spacer(Modifier.height(9.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        planItems.forEachIndexed { index, task ->
                            PlanRow(task = task, isCurrent = index == 0, nowMin = nowMin, dateKey = dateKey)
                        }
                    }
                }
            }
        }

        // Metrics strip — free horizontal scroll, equal gaps, app elastic overscroll.
        val sleepEntry = state.sleepLog.maxByOrNull { it.dateKey }
        val sleepMinutes = sleepEntry?.durationMinutes ?: 0
        val sleepProgress = (sleepMinutes.toFloat() / state.sleepGoalMinutes.coerceAtLeast(1))
            .coerceIn(0f, 1f)
        val sleepLabel = if (sleepEntry == null) "нет записи"
        else "${sleepMinutes / 60} ч ${sleepMinutes % 60} м"

        val weightSpan = kotlin.math.abs(state.weightStart - state.weightGoal).coerceAtLeast(0.1f)
        val weightDone = kotlin.math.abs(state.weightStart - state.weight)
        val weightProgress = (weightDone / weightSpan).coerceIn(0f, 1f)
        val weightLabel = String.format("%.1f кг", state.weight).replace('.', ',')

        val metricsGap = 10.dp
        val metricsEdge = 20.dp
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardW = (maxWidth - metricsEdge * 2 - metricsGap) / 2
            ElasticOverscroll(
                modifier = Modifier.fillMaxWidth(),
                maxVertical = 0.dp,
                maxHorizontal = 48.dp,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(metricsScroll)
                        .padding(horizontal = metricsEdge),
                    horizontalArrangement = Arrangement.spacedBy(metricsGap),
                ) {
                    MetricCard(
                        modifier = Modifier.width(cardW),
                        composition = sandwich,
                        title = "Питание",
                        progress = foodProgress,
                        label = "${state.kcal} из ${state.kcalTarget} ккал",
                        color = LetifyColors.Cal,
                        onAdd = onOpenNutrition,
                        animationsActive = animationsActive,
                    )
                    MetricCard(
                        modifier = Modifier.width(cardW),
                        composition = coke,
                        title = "Вода",
                        progress = waterProgress,
                        label = formatWater(state.waterMl, state.waterTarget),
                        color = LetifyColors.Water,
                        onAdd = { state.addWater(250, "Вода", "water") },
                        animationsActive = animationsActive,
                    )
                    MetricCard(
                        modifier = Modifier.width(cardW),
                        composition = sleepCat,
                        title = "Сон",
                        progress = sleepProgress,
                        label = sleepLabel,
                        color = LetifyColors.Purple,
                        onAdd = onAddSleep,
                        animationsActive = animationsActive,
                    )
                    MetricCard(
                        modifier = Modifier.width(cardW),
                        composition = weightHand,
                        title = "Вес",
                        progress = weightProgress,
                        label = weightLabel,
                        color = LetifyColors.Orange,
                        onAdd = onAddWeight,
                        animationsActive = animationsActive,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

/** One compact line in the home Plan card: icon chip, name, and a short time/status label. */
@Composable
private fun PlanRow(task: TaskItem, isCurrent: Boolean, nowMin: Int, dateKey: String) {
    val live = isCurrent && task.statusAt(nowMin, dateKey) == TaskStatus.Live
    val meta = when {
        live -> "сейчас"
        else -> task.startTime
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(task.color.copy(alpha = if (isCurrent) 0.22f else 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            SolarIcon(name = task.icon, tint = task.color, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(9.dp))
        Text(
            task.name,
            color = if (isCurrent) Letify.colors.text else Letify.colors.muted,
            style = Letify.typography.bodyMedium,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            meta,
            color = if (live) task.color else Letify.colors.muted,
            style = Letify.typography.bodyMedium,
        )
    }
}

/** 3 stacked thumbs. Empty → first is «+», rest silhouettes. */
@Composable
private fun MomentsStack(items: List<MediaItem>) {
    val rotations = listOf(-7f, 2f, 8f)
    val offsets = listOf(0.dp, 14.dp, 28.dp)
    // Card body stays the original 36×44. Separator is an UNDERLAY behind
    // each card (parent `container` color), not a border that eats into the
    // plate — so the tiles look the same size as before, just with a clean
    // gap where they overlap.
    val plate = Letify.colors.bg
    val ring = Letify.colors.container
    val ringW = 2.5.dp
    val cardW = 36.dp
    val cardH = 44.dp
    Box(Modifier.width(68.dp + ringW * 2).height(cardH + ringW * 2)) {
        for (i in 2 downTo 0) {
            val item = items.getOrNull(i)
            val isAdd = items.isEmpty() && i == 0
            // Outer = separator underlay (container color of the parent row).
            Box(
                Modifier
                    .padding(start = offsets[i])
                    .size(width = cardW + ringW * 2, height = cardH + ringW * 2)
                    .zIndex((3 - i).toFloat())
                    .rotate(rotations[i])
                    .clip(RoundedCornerShape(10.dp + ringW))
                    .background(ring),
                contentAlignment = Alignment.Center,
            ) {
                // Inner = original-size opaque plate (+ content).
                Box(
                    Modifier
                        .size(width = cardW, height = cardH)
                        .clip(RoundedCornerShape(10.dp))
                        .background(plate)
                        .then(
                            if (isAdd) Modifier.background(Letify.colors.accent.copy(alpha = 0.18f))
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        item != null && !item.isVideo -> {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        item != null -> {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF2A2A2E)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("▶", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                            }
                        }
                        isAdd -> {
                            Text(
                                "+",
                                color = Letify.colors.accent,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun DayRing(progress: Float, size: Dp) {
    val track = Letify.colors.track
    val accent = Letify.colors.accent
    val p = progress.coerceIn(0f, 1f)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            // Thicker stroke (concept: 14dp) — reads clearer at a glance.
            val sw = 14.dp.toPx()
            val inset = sw / 2f
            val arc = Size(this.size.width - sw, this.size.height - sw)
            val origin = Offset(inset, inset)
            drawArc(track, -90f, 360f, false, origin, arc, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(accent, -90f, 360f * p, false, origin, arc, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Text(
            "${(p * 100).toInt()}%",
            color = Letify.colors.text,
            style = Letify.typography.displayLarge,
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier,
    composition: LottieComposition?,
    title: String,
    progress: Float,
    label: String,
    color: Color,
    onAdd: () -> Unit,
    animationsActive: State<Boolean> = remember { mutableStateOf(true) },
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Letify.colors.container)
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // .value read HERE — MetricCard's own recompose scope, isolated
            // from HomeScreen. Only these 4 small cards react when it flips.
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                isPlaying = animationsActive.value,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                title,
                color = Letify.colors.text,
                style = Letify.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Letify.colors.track),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(7.dp)
                    .background(color, RoundedCornerShape(99.dp)),
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(label, color = Letify.colors.muted, style = Letify.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        NoFeedbackButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                Text("Добавить", color = Color.White, style = Letify.typography.labelMedium)
            }
        }
    }
}

private fun formatWater(ml: Int, target: Int): String {
    if (target >= 1000 || ml >= 1000) {
        fun fmt(v: Float) = String.format("%.2f", v).trimEnd('0').trimEnd('.', ',').replace('.', ',')
        return "${fmt(ml / 1000f)} из ${fmt(target / 1000f)} л"
    }
    return "$ml мл из $target"
}

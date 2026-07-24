package com.letify.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.letify.app.ui.components.NoFeedbackButton
import com.letify.app.ui.components.OverlayHost
import com.letify.app.ui.components.overlayHostShiftFraction
import com.letify.app.ui.components.RoundedSlideOverlay
import com.letify.app.ui.components.rememberParallaxProgress
import com.letify.app.ui.icons.SolarIcon
import com.letify.app.ui.screens.AddHabitScreen
import com.letify.app.ui.screens.AddNutritionScreen
import com.letify.app.ui.screens.AddSleepScreen
import com.letify.app.ui.screens.AddTaskScreen
import com.letify.app.ui.screens.AddWeightScreen
import com.letify.app.ui.screens.AppearanceScreen
import com.letify.app.ui.screens.BindingsScreen
import com.letify.app.ui.screens.EditProfileScreen
import com.letify.app.ui.screens.GoalsScreen
import com.letify.app.ui.screens.HomeScreen
import com.letify.app.ui.screens.LogsScreen
import com.letify.app.ui.screens.NotificationsScreen
import com.letify.app.ui.screens.OtherScreen
import com.letify.app.ui.screens.NutritionScreen
import com.letify.app.ui.screens.PlanScreen
import com.letify.app.ui.screens.ProfileScreen
import com.letify.app.ui.screens.ProgressGoalsScreen
import com.letify.app.ui.screens.WaterHistoryScreen
import com.letify.app.ui.screens.MediaScreen
import com.letify.app.ui.screens.CameraCaptureScreen
import com.letify.app.ui.screens.CameraPrewarm
import com.letify.app.ui.state.LocalAppState
import com.letify.app.ui.state.Tab
import com.letify.app.ui.state.TransitionStyle
import com.letify.app.ui.theme.Letify
import com.letify.app.ui.theme.LetifyColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Identifies which secondary screen is currently active on top of the tabs.
 * Most are full-screen "slide in from the right" overlays; weight is rendered
 * as a real BottomSheet because it's a single-value picker.
 */
// Tab-switch push: same gentle ease-out curve + duration the navbar pill uses,
// so the bottom-tab swap and the indicator glide on one consistent motion.
private val TabPushEasing = CubicBezierEasing(0.32f, 0.72f, 0.0f, 1.0f)
private const val TabPushMs = 320
// Home⇄Profile hero: longer + softer so the flight feels like a glide, not a snap.
private val HeroFlightEasing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
private const val HeroFlightMs = 520

// Smooth decelerate for the camera slide-up from bottom.
private val CameraSlideEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
private const val CameraSlideInMs = 380
private const val CameraSlideOutMs = 300

sealed interface AddOverlay {
    // editId != null → open the create screen pre-filled to EDIT that item
    // (from the long-press «Изменить» menu) instead of creating a new one.
    data class Habit(val editId: Int? = null) : AddOverlay
    data class Task(val editId: Int? = null) : AddOverlay
    data object Nutrition : AddOverlay
    data object NutritionHub : AddOverlay
    data object Weight : AddOverlay
    data object Sleep : AddOverlay
    data object EditProfile : AddOverlay
    data object Goals : AddOverlay
    data object Appearance : AddOverlay
    data object Notifications : AddOverlay
    data object Bindings : AddOverlay
    data object Tiwi : AddOverlay
    data object Other : AddOverlay
    data object Logs : AddOverlay
    data object ProgressGoals : AddOverlay
    data object WaterHistory : AddOverlay
    data object Media : AddOverlay
}

/**
 * Stable key for the SaveableStateHolder so a screen keeps its saveable state
 * (scroll offset, expanded sections…) when it moves between the active-top slot
 * and the static underlay slot. Data-class overlays include their id so two
 * different detail/schedule screens never collide.
 */
private fun AddOverlay.stateKey(): String = this::class.java.simpleName

@Composable
fun LetifyApp() {
    val state = LocalAppState.current

    // Snap to whatever the user picked as their default landing tab once on
    // first composition of the app shell.
    //
    // Keying this on `state.defaultTab` (the previous behaviour) was a
    // landmine: opening Appearance → Навбар and picking a new default tab
    // would mutate `currentTab` while the user was still inside the nested
    // overlay, kicking off a tab-content transition behind the overlay
    // stack. Swiping back from Навбар then races against that transition,
    // which crashed the app on some devices. The setting is a "next launch"
    // preference, not a "switch right now" action, so we only honour it on
    // first entry. From then on `currentTab` is owned solely by direct nav
    // (tab bar tap / overlay nav handlers).
    LaunchedEffect(Unit) {
        state.currentTab = state.defaultTab
    }

    // Stack of overlays so a sub-screen can pop back to its parent
    // (e.g. Logs opened from Other slides back into Other, not all the
    // way to the home screen).
    //
    // Rendering model: the *top* of the stack is the active overlay —
    // it gets a RoundedSlideOverlay that drives the host parallax (so
    // the home tab content slides/zooms behind it), is interactive, and
    // can be dismissed by swipe-back. If the stack has a level below
    // (e.g. [Other, Logs]) we render that **second-from-top** overlay
    // statically as an "underlay" behind the active one, with no swipe
    // gestures and a no-op back. That guarantees:
    //   - When the user taps Логи from Другое, the slide-in of Logs
    //     reveals Other behind it — not the home tab (Profile). The
    //     previous code unmounted Other on the key change and the user
    //     briefly saw Profile through the gap.
    //   - When the user swipes Logs back, Logs slides out to the right
    //     revealing the still-rendered Other below. After onDismissed
    //     fires we re-mount Other as the new active top, but it was
    //     already at its final on-screen position so there's no second
    //     slide-in animation (see `animateIn` below).
    //
    // `lastAction` tells the active RoundedSlideOverlay whether the
    // user got here via PUSH (animate in from the right) or POP (it
    // was already on screen as an underlay — no entry animation).
    var overlayStack by remember { mutableStateOf<List<AddOverlay>>(emptyList()) }
    var lastAction by remember { mutableStateOf("init") }

    // Camera sheet motion — hard rule: ZERO camera/HAL work on animation frames.
    //
    // OPEN:  slide a lightweight shell (no PreviewView, no bind). When the
    //        slide settles → cameraReady=true → bind + fade-in first frame.
    // CLOSE: start the slide IMMEDIATELY (no freeze, no bitmap copy, no
    //        unbind-before-motion). After the sheet is off-screen → unbind
    //        and dispose. Pre-work before animateTo was the close "лаг".
    // Progress is read only inside graphicsLayer so the tree isn't
    // recomposed every frame.
    val cameraScope = rememberCoroutineScope()
    val appContext = LocalContext.current
    var cameraVisible by remember { mutableStateOf(false) }
    val cameraProgress = remember { Animatable(0f) }
    var cameraReady by remember { mutableStateOf(false) }
    var cameraAnimJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(Unit) { CameraPrewarm.warm(appContext) }
    val openCamera: () -> Unit = {
        CameraPrewarm.warm(appContext)
        cameraAnimJob?.cancel()
        cameraReady = false
        cameraVisible = true
        cameraAnimJob = cameraScope.launch {
            cameraProgress.animateTo(
                1f,
                animationSpec = tween(CameraSlideInMs, easing = CameraSlideEasing),
            )
            // Bind only after motion fully stops.
            cameraReady = true
        }
    }
    val closeCamera: () -> Unit = {
        cameraAnimJob?.cancel()
        // Animate IMMEDIATELY — no freeze, no unbind, no bitmap. Unbind runs
        // only after the sheet is fully off-screen (cameraVisible=false).
        cameraAnimJob = cameraScope.launch {
            cameraProgress.animateTo(
                0f,
                animationSpec = tween(CameraSlideOutMs, easing = CameraSlideEasing),
            )
            cameraReady = false
            cameraVisible = false
        }
    }
    val overlay: AddOverlay? = overlayStack.lastOrNull()
    val underlay: AddOverlay? = if (overlayStack.size >= 2) overlayStack[overlayStack.size - 2] else null
    val push: (AddOverlay) -> Unit = { o -> overlayStack = overlayStack + o; lastAction = "push" }
    val pop: () -> Unit = { overlayStack = overlayStack.dropLast(1); lastAction = "pop" }
    // Root-level bottom sheet (weight / sleep) opened from inside the
    // Progress-Goals screen. Rendered as a sibling ABOVE the overlay stack
    // rather than pushed onto it — so Progress-Goals stays the active overlay
    // and never re-mounts. That's what keeps its active tab, per-section
    // scroll position and period from snapping back when the user taps
    // "+ вес" / "+ сон" (the jump the user reported). null = no sheet open.
    var rootSheet by remember { mutableStateOf<AddOverlay?>(null) }
    val parallax = rememberParallaxProgress()
    // Sink for nested overlays. When the stack is >= 2 levels deep the
    // active top RoundedSlideOverlay must NOT drive the host parallax,
    // because the underlay (Other under Logs) is rendered as a fully
    // opaque sibling above the home tab. If the new Logs RSO mirrored
    // its dismissProgress=1 starting value into the real parallax, the
    // navbar (whose alpha = parallax) would flash to 1 for one frame
    // every time the user opens a nested screen — that's the wrong
    // entry animation the user reported for Logs from Другое. The
    // dummy sink absorbs the mirror writes so nested entry/exit reads
    // exactly like a normal slide-in over the parent.
    // The top overlay at depth >= 2 drives THIS state instead of the
    // host parallax. We also read it from the underlay Box so the
    // screen underneath (e.g. Other under Logs) slides slightly to
    // the left as the top overlay slides in from the right — the
    // iOS-style parallax the rest of the app already does for the
    // home pager via OverlayHost. Initial value is 1f (no shift) so
    // there's no flicker before the first RSO write.
    // Driver for nested (depth >= 2) push/pop transitions. Re-created on every
    // stack change STARTING AT 1f, so a freshly-pushed nested screen begins
    // fully off-screen to the right instead of momentarily rendering at rest
    // (progress = 0) for one frame before the slide-in kicks off — that single
    // wrong frame was the «мигание при Запланировать» the user kept seeing
    // (the shared sink used to sit at 0 from the previous level). Both the
    // incoming top overlay AND the static parent underneath read THIS instance,
    // so they move in lockstep: the parent now slides too, which makes nested
    // transitions finally obey the chosen «Сдвиг / Наплыв» style instead of the
    // parent always staying frozen (the «переход не тот, что в настройках» bug).
    // Init depends on the action that produced this stack:
    //  - PUSH → start at 1f so the incoming top begins fully off the right edge
    //    and slides in (a freshly-created Animatable rendered at 0 for one frame
    //    was the old «мигание при Запланировать» on OPEN).
    //  - POP  → start at 0f so the screen we popped BACK to (which becomes the new
    //    active top) is centered on its very first frame. Otherwise the recreated
    //    Animatable sat at 1f, the incoming top's graphicsLayer read progress=1
    //    and drew it off-screen-right for one frame BEFORE its LaunchedEffect
    //    snapTo(0) ran — exposing the underlay (the витрина) underneath = the
    //    «мигание витрины при закрытии Запланировать» the user still saw.
    val nestedParallax = remember(overlayStack) {
        Animatable(if (lastAction == "pop") 0f else 1f)
    }

    // Keeps each overlay's *saveable* UI state (e.g. the витрина's vertical
    // scroll offset) alive when the SAME screen moves between the active-top
    // slot and the static underlay slot. Without it, tapping a list row pushed
    // a child, which re-mounted the витрина in the underlay slot with a fresh
    // scroll state → it snapped back to the top («резко перематывается вверх»,
    // the bug previously fixed for Progress-Goals).
    val overlayStateHolder = rememberSaveableStateHolder()

    // Frosted-glass source — captures the tab content drawn under
    // the navbar so the bar can render a real RenderEffect-blurred
    // snapshot through it. The source is on a SIBLING wrapper, not
    // on the root that also holds the navbar, so the navbar's own
    // pixels don't end up in the source (avoids the empty-navbar
    // feedback we saw in r33).
    val hazeState = remember { HazeState() }

    // SaveableStateHolder for the tab pager (see SaveableStateProvider below).
    val tabStateHolder = rememberSaveableStateHolder()

    // True while a tab-switch slide is in flight. The navbar uses this to drop
    // its (expensive, per-frame) frosted-glass blur DURING the slide and restore
    // it once the screen has settled. Rationale: on a tab switch the content
    // slides UNDER the stationary navbar, so haze re-samples the blur every frame;
    // during the slow ease-out tail (sub-pixel/frame) that re-sampling reads as a
    // shimmer = the "подрагивание при остановке" the user felt. While moving we
    // show a flat tint of the SAME colour (no blur, no re-sampling, no shimmer),
    // then crossfade the real blur back in ~1 frame AFTER motion fully stops — so
    // the swap itself never happens while the eye is tracking motion. We add a
    // small buffer past TabPushMs so the blur returns strictly after the last
    // moving frame, never one frame early (which would re-introduce a hitch).
    // Driven by CachedTabPager.onSettledChange: false the instant a tab-switch
    // slide starts, true exactly when it settles (no fixed delay guesswork).
    var tabSettled by remember { mutableStateOf(true) }

    // Single identity for avatar+name (Home ⇄ Profile).
    // There is exactly ONE composable that paints them — at the root.
    // Home/Profile only keep empty size slots (placeholders). No copy, no hide-of-real.
    //
    // HomeScreen/ProfileScreen already report their REAL, measured avatar and
    // name rects via onAvatarBoundsChanged/onNameBoundsChanged (onGloballyPositioned
    // + boundsInRoot) — that plumbing existed but was never wired up here, so the
    // flight used to fly to/from `estimated*Rect()` guesses instead (hand-rolled
    // "charWidth ≈ fontPx * 0.62" math for the name). That guess doesn't match real
    // Cyrillic glyph metrics, so the name landed a few px off-center in Profile —
    // that's the "ник ... выравнивается неправильно" bug. We now keep the last
    // real measurement for each of the 4 rects and prefer it over the estimate the
    // instant it's available (estimate is only a first-frame fallback, before
    // Home/Profile have measured once).
    var realHomeAvatar by remember { mutableStateOf<Rect?>(null) }
    var realHomeName by remember { mutableStateOf<Rect?>(null) }
    var realProfileAvatar by remember { mutableStateOf<Rect?>(null) }
    var realProfileName by remember { mutableStateOf<Rect?>(null) }

    Box(Modifier.fillMaxSize().background(Letify.colors.bg)) {
        OverlayHost(parallaxProgress = parallax) {
            // Haze SOURCE = tab content only. Both this source AND the navbar
            // below now live INSIDE OverlayHost, so they translate together as
            // ONE canvas during a push: the navbar's blurred backdrop no longer
            // "dances" under content sliding independently (that was the jank),
            // and the bar slides away with the screen like one polotno. Overlays
            // are still drawn AFTER OverlayHost (below) so they cover it.
            Box(Modifier.fillMaxSize().haze(hazeState)) {
                CachedTabPager(
                    current = state.currentTab,
                    order = state.navbarOrder,
                    onSettledChange = { tabSettled = it },
                    modifier = Modifier.fillMaxSize(),
                    identity = { progress ->
                        val density = LocalDensity.current
                        val statusBarPx = WindowInsets.statusBars.getTop(density).toFloat()
                        val screenW =
                            with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
                        val name = state.userName.ifBlank { "друг" }
                        UserIdentity(
                            progress = progress,
                            sourceAvatar = realHomeAvatar
                                ?: estimatedHomeAvatarRect(density, statusBarPx),
                            targetAvatar = realProfileAvatar
                                ?: estimatedProfileAvatarRect(density, statusBarPx, screenW),
                            sourceName = realHomeName
                                ?: estimatedHomeNameRect(density, statusBarPx, name),
                            targetName = realProfileName
                                ?: estimatedProfileNameRect(density, statusBarPx, screenW, name),
                            name = name,
                            photoUrl = state.telegramUser?.photoUrl,
                            letter = name.firstOrNull()?.uppercase() ?: "?",
                        )
                    },
                ) { tab, usePlaceholder ->
                    tabStateHolder.SaveableStateProvider(tab.name) {
                        when (tab) {
                            Tab.Home -> HomeScreen(
                                onAddWeight = { push(AddOverlay.Weight) },
                                onOpenNutrition = { push(AddOverlay.NutritionHub) },
                                onAddSleep = { push(AddOverlay.Sleep) },
                                onAddMeal = { push(AddOverlay.Nutrition) },
                                onOpenProfile = { state.currentTab = Tab.Profile },
                                onOpenPlan = { state.currentTab = Tab.Plan },
                                onOpenMoments = { push(AddOverlay.Media) },
                                // Placeholder only — the one real identity is at the root.
                                hideAvatarName = usePlaceholder,
                                onAvatarBoundsChanged = { realHomeAvatar = it },
                                onNameBoundsChanged = { realHomeName = it },
                            )
                            Tab.Nutrition -> {
                                androidx.compose.runtime.LaunchedEffect(Unit) {
                                    state.currentTab = Tab.Home
                                }
                                Box(Modifier.fillMaxSize())
                            }
                            Tab.Plan -> PlanScreen(
                                onBack = { state.currentTab = Tab.Home },
                                onAddHabit = { push(AddOverlay.Habit()) },
                                onAddTask = { push(AddOverlay.Task()) },
                                onEditHabit = { id -> push(AddOverlay.Habit(id)) },
                                onEditTask = { id -> push(AddOverlay.Task(id)) },
                            )
                            Tab.Profile -> ProfileScreen(
                                onBack = { state.currentTab = Tab.Home },
                                onEditProfile = { push(AddOverlay.EditProfile) },
                                onGoals = { push(AddOverlay.Goals) },
                                onAppearance = { push(AddOverlay.Appearance) },
                                onNotifications = { push(AddOverlay.Notifications) },
                                onOther = { push(AddOverlay.Other) },
                                onProgressDetail = { push(AddOverlay.ProgressGoals) },
                                onMedia = { push(AddOverlay.Media) },
                                onQuickCamera = { openCamera() },
                                onQuickScan = { push(AddOverlay.Tiwi) },
                                onQuickWeight = { push(AddOverlay.Weight) },
                                hideAvatarName = usePlaceholder,
                                onAvatarBoundsChanged = { realProfileAvatar = it },
                                onNameBoundsChanged = { realProfileName = it },
                            )
                        }
                    }
                }
            }

        }

        // Underlay — only the second-from-top overlay, rendered
        // statically full-screen behind the active one (no slide, no
        // swipe-back, no-op onBack). Keeps the parent visible during
        // child push/pop so the home tab never flashes through.
        underlay?.let { u ->
            if (u != AddOverlay.Weight) {
                // Nested push now behaves EXACTLY like a top-level push: the parent
                // (underlay) slides left in lockstep with the incoming child, driven
                // by the SAME `nestedParallax` Animatable the active top reads —
                // identical to how OverlayHost shifts the home content at depth 1.
                // This restores the shared-axis «Сдвиг»/«Наплыв» look the user wanted
                // («переход должен быть как на всех других экранах»). The one-frame
                // витрина flash that previously came with a sliding underlay was NOT
                // caused by the slide itself but by `nestedParallax` re-initialising
                // at 1f on a POP (see above) — fixed there, so we can slide safely.
                val style = state.transitionStyle
                val shiftFraction = overlayHostShiftFraction(style)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = nestedParallax.value.coerceIn(0f, 1f)
                            translationX = -(1f - p) * size.width * shiftFraction
                        }
                        .background(Letify.colors.bg)
                ) {
                    overlayStateHolder.SaveableStateProvider(u.stateKey()) {
                        OverlayContent(
                            current = u,
                            animatedBack = {},
                            onPushLogs = {},
                        )
                    }
                    // Cover style («Наплыв») dims the receding parent, matching the
                    // top-level OverlayHost dim so nested transitions look the same.
                    if (style == TransitionStyle.Cover) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .graphicsLayer {
                                    alpha = (1f - nestedParallax.value.coerceIn(0f, 1f)) * 0.16f
                                }
                                .background(Color.Black)
                        )
                    }
                }
            }
        }

        // Multi-field forms get the full-screen "slide in from the right"
        // overlay. Weight is a BottomSheet (rendered outside this branch).
        overlay?.let { current ->
            if (current == AddOverlay.Weight) {
                AddWeightScreen(onBack = { pop() })
            } else {
                // Only animate-in for PUSH actions. If we got here via
                // POP (the user dismissed a child overlay), this view
                // was already on screen as an underlay — re-mounting it
                // and re-running the slide-in would look like the new
                // top "appears" from the right one extra time.
                val animateInTop = lastAction != "pop"
                val topParallax = if (overlayStack.size >= 2) nestedParallax else parallax
                key(current, animateInTop) {
                    RoundedSlideOverlay(
                        parallaxProgress = topParallax,
                        onDismissed = { pop() },
                        animateIn = animateInTop,
                    ) { animatedBack ->
                        overlayStateHolder.SaveableStateProvider(current.stateKey()) {
                            OverlayContent(
                                current = current,
                                animatedBack = animatedBack,
                                onPushLogs = { push(AddOverlay.Logs) },
                                onPushBindings = { push(AddOverlay.Bindings) },
                                // Weight / sleep adders open as a ROOT bottom sheet
                                // (rootSheet) instead of being pushed — keeps the
                                // Progress-Goals screen mounted underneath.
                                onPushWeight = { rootSheet = AddOverlay.Weight },
                                onPushSleep = { rootSheet = AddOverlay.Sleep },
                                onOpenCameraExpand = { openCamera() },
                                onPushNutrition = { push(AddOverlay.Nutrition) },
                                onPushWaterHistory = { push(AddOverlay.WaterHistory) },
                            )
                        }
                    }
                }
            }
        }
        // Root-level bottom sheets (weight / sleep). Rendered LAST so they
        // sit on top of every overlay and the navbar. Driven by `rootSheet`
        // rather than the overlay stack, so the screen that opened them
        // (Progress-Goals) stays mounted and keeps its scroll/tab/period.
        when (rootSheet) {
            AddOverlay.Weight -> AddWeightScreen(onBack = { rootSheet = null })
            AddOverlay.Sleep -> AddSleepScreen(onBack = { rootSheet = null })
            else -> {}
        }

        // Camera full-screen slide-up. Keyed ONLY on `cameraVisible` so the
        // Animatable progress is read inside graphicsLayer (draw phase) and
        // does NOT recompose CameraCaptureScreen / AndroidView every frame —
        // that recomposition was what made the live TextureView flicker and
        // the picture "snap" away during open/close.
        // Close keeps cameraVisible=true until the slide finishes, so the
        // feed rides translationY the whole way down.
        if (cameraVisible) {
            Box(Modifier.fillMaxSize().zIndex(50f)) {
                // Soft scrim that fades with the slide (draw-time read).
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = cameraProgress.value.coerceIn(0f, 1f) * 0.45f
                        }
                        .background(Color.Black),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = cameraProgress.value.coerceIn(0f, 1f)
                            translationY = (1f - p) * size.height
                        }
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                ) {
                    CameraCaptureScreen(
                        onBack = closeCamera,
                        onCaptured = {},
                        readyToBind = cameraReady,
                    )
                }
            }
        }

        // Crash reports are written to disk by CrashReporter on uncaught
        // exception and surfaced passively via Profile → Другое → Логи.
        // The previous launch-time dialog interrupted the cold-start flow
        // after every crash and looked alarming — the new screen lets the
        // user copy logs on demand without blocking the UI.
    }
}

/**
 * Renders the body of a single overlay level. Extracted so both the
 * static underlay (no animation, no back) and the interactive top
 * (slide+swipe via RoundedSlideOverlay) share one switch and stay in
 * sync when new overlay types are added.
 */
@Composable
private fun OverlayContent(
    current: AddOverlay,
    animatedBack: () -> Unit,
    onPushLogs: () -> Unit,
    onPushWeight: () -> Unit = {},
    onPushSleep: () -> Unit = {},
    onPushBindings: () -> Unit = {},
    onOpenCameraExpand: () -> Unit = {},
    onPushNutrition: () -> Unit = {},
    onPushWaterHistory: () -> Unit = {},
) {
    when (current) {
        is AddOverlay.Habit -> AddHabitScreen(onBack = animatedBack, editId = current.editId)
        is AddOverlay.Task -> AddTaskScreen(onBack = animatedBack, editId = current.editId)
        AddOverlay.Nutrition -> AddNutritionScreen(onBack = animatedBack)
        AddOverlay.NutritionHub -> NutritionScreen(
            onBack = animatedBack,
            onAddMeal = onPushNutrition,
            onWaterHistory = onPushWaterHistory,
        )
        AddOverlay.Sleep -> AddSleepScreen(onBack = animatedBack)
        AddOverlay.Weight -> {} // weight is a bottom-sheet, handled elsewhere
        AddOverlay.EditProfile -> EditProfileScreen(onBack = animatedBack)
        AddOverlay.Goals -> GoalsScreen(onBack = animatedBack)
        AddOverlay.Appearance -> AppearanceScreen(onBack = animatedBack)
        AddOverlay.Notifications -> NotificationsScreen(onBack = animatedBack)
        AddOverlay.Bindings -> BindingsScreen(onBack = animatedBack)
        AddOverlay.Tiwi -> TiwiPlaceholder(onBack = animatedBack)
        AddOverlay.Other -> OtherScreen(
            onBack = animatedBack,
            onLogs = onPushLogs,
            onBindings = onPushBindings,
        )
        AddOverlay.Logs -> LogsScreen(onBack = animatedBack)
        AddOverlay.WaterHistory -> WaterHistoryScreen(onBack = animatedBack)
        AddOverlay.ProgressGoals -> ProgressGoalsScreen(
            onBack = animatedBack,
            onAddWeight = onPushWeight,
            onAddSleep = onPushSleep,
        )
        AddOverlay.Media -> MediaScreen(
            onBack = animatedBack,
            onOpenCamera = onOpenCameraExpand,
        )
    }
}

@Composable
private fun TiwiPlaceholder(onBack: () -> Unit) {
    // The user explicitly asked that "Тифи" not open any real content for
    // now — just a polite stub with a back arrow so the entry still feels
    // wired up.
    Box(
        Modifier
            .fillMaxSize()
            .background(Letify.colors.bg),
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            com.letify.app.ui.components.SettingsHeader(title = "Letify", onBack = onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SolarIcon(
                        name = "smile-circle-outline",
                        tint = Letify.colors.muted.copy(alpha = 0.6f),
                        size = 64.dp,
                    )
                    Box(Modifier.size(14.dp))
                    Text(
                        "Скоро",
                        color = Letify.colors.text,
                        style = Letify.typography.headlineMedium,
                    )
                    Box(Modifier.size(6.dp))
                    Text(
                        "Этот раздел пока в работе",
                        color = Letify.colors.muted,
                        style = Letify.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/**
 * Tab pager that CACHES screens instead of disposing them.
 *
 * The old `AnimatedContent` tore the outgoing tab's whole composition down and
 * rebuilt the incoming one from scratch on every switch. For the heavy Plan
 * screen (a non-lazy task list) that rebuild dropped frames every time you
 * tapped it in the navbar — the "очень сильно лагает открытие экрана задач" lag,
 * and the janky tab-slide (the incoming screen was being composed mid-animation).
 *
 * Here every tab that has ever been shown stays in the composition, parked
 * off-screen with `alpha = 0` (so it draws nothing while idle). Switching just
 * slides the two involved tabs across — no compose work, no rebuild — so
 * re-entry is instant and the slide runs at full frame-rate. Only a direct
 * two-screen push is animated (from → to), exactly like before, regardless of
 * how far apart the tabs sit in the navbar.
 */
@Composable
private fun CachedTabPager(
    current: Tab,
    order: List<Tab>,
    onSettledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The single avatar+name identity. `progress` is a DEFERRED reader —
     * call it only from inside a `graphicsLayer {}` (or other draw-phase)
     * block, never from the composable body — that's what lets the whole
     * flight run without recomposing anything every frame. Absolute scale:
     * 0 = home layout, 1 = profile layout. Drawn whenever Home or Profile
     * owns the surface — at rest and during the flight. Screens only
     * reserve empty slots.
     */
    identity: (@Composable (progress: () -> Float) -> Unit)? = null,
    content: @Composable (tab: Tab, usePlaceholder: Boolean) -> Unit,
) {
    val visited = remember { mutableStateListOf<Tab>() }
    if (current !in visited) visited.add(current)

    var fromTab by remember { mutableStateOf(current) }
    var toTab by remember { mutableStateOf(current) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(current) {
        if (current != toTab) {
            val prev = toTab
            val next = current
            val isProfilePair =
                (prev == Tab.Home && next == Tab.Profile) ||
                (prev == Tab.Profile && next == Tab.Home)
            fromTab = prev
            toTab = next
            onSettledChange(false)
            progress.snapTo(0f)
            progress.animateTo(
                1f,
                animationSpec = tween(
                    durationMillis = if (isProfilePair) HeroFlightMs else TabPushMs,
                    easing = if (isProfilePair) HeroFlightEasing else TabPushEasing,
                ),
            )
            onSettledChange(true)
            fromTab = current
        }
    }

    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        // IMPORTANT: nothing below this point reads `progress.value` at the
        // composable-body level anymore. `pending`/`activeFrom`/`activeTo`/
        // `isAvatarPair`/`settledHome`/`settledProfile`/`showIdentity`/
        // `activeDir` only depend on WHICH tabs are involved (current /
        // fromTab / toTab), which change at most once per transition — not
        // every frame. The old code additionally read `progress.value` here
        // (`val p = ...`) purely to compute per-tab offsets, which meant
        // Compose recomposed this entire pager — and every Box, and the
        // identity overlay's Text/AsyncImage — on every single animation
        // frame (~30+ times over the 520ms hero flight). That per-frame
        // recomposition + remeasure + text re-shaping was the actual
        // "лагает" stutter, not the tween/easing itself. `progress.value` is
        // now read ONLY inside each `graphicsLayer {}` block below, which
        // Compose can re-run on the draw phase alone, without recomposing
        // anything above it.
        val pending = current != toTab
        val activeFrom = if (pending) toTab else fromTab
        val activeTo = if (pending) current else toTab
        val isAvatarPair =
            (activeFrom == Tab.Home && activeTo == Tab.Profile) ||
            (activeFrom == Tab.Profile && activeTo == Tab.Home)
        val settledHome = activeFrom == Tab.Home && activeTo == Tab.Home
        val settledProfile = activeFrom == Tab.Profile && activeTo == Tab.Profile
        // One identity whenever Home or Profile is the active surface.
        val showIdentity = isAvatarPair || settledHome || settledProfile
        val activeDir =
            if (order.indexOf(activeTo).coerceAtLeast(0) >= order.indexOf(activeFrom).coerceAtLeast(0))
                1f else -1f

        // Resolves the live, clamped progress. Deliberately a plain (non-@Composable)
        // local function, not a `val`, so calling it is a snapshot-state read that
        // only happens where it's actually invoked (inside graphicsLayer blocks) —
        // never during composition.
        fun resolvedP(): Float {
            val raw = progress.value
            return if (pending && raw >= 0.999f) 0f else raw
        }

        visited.forEach { tab ->
            val parked = tab != activeTo && tab != activeFrom
            val isAvatarSlide = isAvatarPair && !parked
            // Home/Profile use placeholders whenever the root identity is shown —
            // which is also true for the whole duration of an avatar-pair slide.
            val usePlaceholder = showIdentity && (tab == Tab.Home || tab == Tab.Profile)

            // ONE Box / ONE `content(tab, ...)` call site for every tab, in every
            // state. Only the modifier VALUES branch on `isAvatarSlide` below —
            // there used to be two separate `content(tab, ...)` calls written at
            // two different places in the source (one for the avatar-pair slide,
            // one for everything else). Even though it was the same lambda, Compose
            // identifies a composable's remembered state by its call SITE, not by
            // runtime tab equality — so the instant `isAvatarPair` flipped (opening
            // or closing Profile), Home's whole subtree was torn down at one call
            // site and rebuilt from scratch at the other. That's what caused both
            // bugs the user reported: the Lottie stickers restarted their
            // composition load (visible as a flash/blink), and the metrics row's
            // `ScrollState` was recreated at 0 (visible as the cards snapping back
            // to the start) — even a "остаться после" flick to the end of the
            // metric cards was invisible to the transition until this recreation
            // hit. Keeping a single call site means the SAME HomeScreen instance
            // (same Lottie compositions, same ScrollState) stays mounted straight
            // through the whole open/close flight.
            Box(
                Modifier
                    .fillMaxSize()
                    // Profile is ALWAYS the foreground layer of the avatar-pair —
                    // it's the one that travels the full screen width (dx up to
                    // ±w), while Home only ever does the small `parallax` (0.28×w)
                    // shift as the receding/entering backdrop. That asymmetry is
                    // the whole point of the hero effect, so the z-order must match
                    // it in BOTH directions (a plain "activeTo on top" would put
                    // Home above the exiting Profile on close — see the old
                    // "second identical screen sliding behind it" glitch).
                    .zIndex(
                        if (isAvatarSlide) (if (tab == Tab.Profile) 1f else 0f)
                        else (if (tab == activeTo) 1f else 0f),
                    )
                    .graphicsLayer {
                        val p = resolvedP()
                        if (isAvatarSlide) {
                            val parallax = 0.28f
                            translationX = when {
                                tab == activeFrom && activeTo == Tab.Profile -> -p * parallax * w
                                tab == activeTo && activeTo == Tab.Profile -> (1f - p) * w
                                tab == activeFrom && activeTo == Tab.Home -> p * w
                                tab == activeTo && activeTo == Tab.Home -> -(1f - p) * parallax * w
                                else -> 0f
                            }
                            alpha = 1f
                        } else {
                            translationX = when (tab) {
                                activeTo -> (1f - p) * activeDir * w
                                activeFrom -> -p * activeDir * w
                                else -> w
                            }
                            alpha = if (parked) 0f else 1f
                        }
                        clip = true
                    }
                    // Opaque page bg — Profile/ScreenScaffold used to be
                    // transparent, so home bled through during the slide.
                    // MUST come AFTER graphicsLayer in the chain: a
                    // .background() placed BEFORE graphicsLayer draws on
                    // the outer/untransformed canvas, so it painted the
                    // FULL viewport at its static layout position — not
                    // the translated/clipped position — regardless of
                    // `dx`. That made the entering tab's plate cover the
                    // whole screen from frame 0 (the other tab "resko
                    // ischezaet") while its actual content still had to
                    // slide in underneath that plate ("prosvechivaet").
                    // Moving background after graphicsLayer makes it
                    // part of the SAME transformed+clipped layer as the
                    // content, so plate and content move as one.
                    .background(Letify.colors.bg),
            ) {
                content(tab, usePlaceholder)
            }
        }

        if (showIdentity && identity != null) {
            Box(Modifier.fillMaxSize().zIndex(20f)) {
                identity {
                    val p = resolvedP()
                    when {
                        isAvatarPair && activeTo == Tab.Profile -> p
                        isAvatarPair && activeTo == Tab.Home -> 1f - p
                        settledProfile -> 1f
                        else -> 0f // settled home
                    }
                }
            }
        }
    }
}

@Composable
private fun UserIdentity(
    // Deferred reader — MUST only be called inside a graphicsLayer {} (or
    // other draw-phase) block below, never at the top of this function body.
    // Calling it here would make this whole composable (both Texts, the
    // AsyncImage, both Boxes) recompose on every animation frame again,
    // which is exactly the per-frame cost we're removing.
    progress: () -> Float,
    sourceAvatar: Rect,
    targetAvatar: Rect,
    sourceName: Rect,
    targetName: Rect,
    name: String,
    photoUrl: String?,
    letter: String,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val avatarBrush = Brush.linearGradient(
        listOf(Letify.colors.accent, LetifyColors.TilePink),
    )

    // Both boxes are measured/laid out EXACTLY ONCE, at the larger of the
    // two (source, target) footprints — Profile's avatar/name are always
    // the bigger of the pair, so in practice this is the Profile size.
    // Every frame after that first layout pass is a pure graphicsLayer
    // scale + translate, which is a draw-phase-only operation (no measure,
    // no layout, no text re-shaping).
    //
    // The old version instead changed the Box's `.size(dp)` AND the text's
    // `fontSize` from the live progress every frame. Both of those are
    // layout inputs in Compose — resizing the box forces a remeasure of
    // its subtree, and changing fontSize forces the Text to re-shape its
    // glyphs from scratch — so the flight was doing a full measure + text
    // layout pass ~30+ times over its 520ms duration. That per-frame
    // layout/shaping work (not the tween/easing) was the actual stutter.
    val avatarBoxPx = max(sourceAvatar.width, targetAvatar.width)
    val avatarBoxDp = with(density) { avatarBoxPx.toDp() }

    Box(
        Modifier
            .size(avatarBoxDp)
            .graphicsLayer {
                val t = progress().coerceIn(0f, 1f)
                val rect = lerp(sourceAvatar, targetAvatar, t)
                val scale = if (avatarBoxPx > 0f) rect.width / avatarBoxPx else 1f
                scaleX = scale
                scaleY = scale
                translationX = rect.left
                translationY = rect.top
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .clip(CircleShape)
            .background(avatarBrush, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // Fixed at the full-size (Profile) glyph size — shrinks visually
        // along with the rest of the box via the graphicsLayer scale above,
        // instead of being re-shaped at a new fontSize every frame.
        Text(
            letter,
            color = Color.White,
            fontSize = with(density) { (targetAvatar.width * 0.38f).toSp() },
            fontWeight = FontWeight.Bold,
        )
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(photoUrl).crossfade(false).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }

    // Same treatment for the name: one fixed-size box (sized to the wider
    // of the two real, measured name rects — see the realHomeName/
    // realProfileName wiring in LetifyApp), scaled/translated per frame.
    val nameBoxWPx = max(sourceName.width, targetName.width)
    val nameBoxHPx = max(sourceName.height, targetName.height)
    Box(
        Modifier
            .size(
                with(density) { nameBoxWPx.toDp() },
                with(density) { nameBoxHPx.toDp() },
            )
            .graphicsLayer {
                val t = progress().coerceIn(0f, 1f)
                val rect = lerp(sourceName, targetName, t)
                val scale = if (nameBoxWPx > 0f) rect.width / nameBoxWPx else 1f
                scaleX = scale
                scaleY = scale
                translationX = rect.left
                translationY = rect.top
                transformOrigin = TransformOrigin(0f, 0f)
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Fixed at Profile's real name size (ScreenScaffold header uses
        // headlineLarge = 22.sp) — same reasoning as the avatar letter
        // above: this box's own graphicsLayer scale shrinks it visually to
        // match Home's smaller size, so the Text itself never needs to be
        // re-shaped at a different fontSize mid-flight.
        Text(
            name,
            color = Letify.colors.text,
            fontSize = Letify.typography.headlineLarge.fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

// Layout constants mirrored from HomeScreen / ProfileScreen — do not drift.
private fun estimatedHomeAvatarRect(density: Density, statusBarPx: Float): Rect {
    // Column top pad 6 + row vertical pad 10 = 16; horizontal pad 20; size 34
    val left = with(density) { 20.dp.toPx() }
    val top = statusBarPx + with(density) { 16.dp.toPx() }
    val size = with(density) { 34.dp.toPx() }
    return Rect(left, top, left + size, top + size)
}

private fun estimatedHomeNameRect(density: Density, statusBarPx: Float, name: String): Rect {
    val avatarTop = statusBarPx + with(density) { 16.dp.toPx() }
    val avatarSize = with(density) { 34.dp.toPx() }
    val centerY = avatarTop + avatarSize / 2f
    // 20 pad + 34 avatar + 12 spacer
    val left = with(density) { 66.dp.toPx() }
    val fontPx = with(density) { 19.sp.toPx() }
    val height = fontPx * 1.2f
    val width = name.length.coerceAtLeast(1) * fontPx * 0.62f
    return Rect(left, centerY - height / 2f, left + width, centerY + height / 2f)
}

private fun estimatedProfileAvatarRect(density: Density, statusBarPx: Float, screenWidthPx: Float): Rect {
    // ScreenScaffold statusBars + header row ~56dp + avatar top pad 2dp
    val top = statusBarPx + with(density) { 58.dp.toPx() }
    val size = with(density) { 108.dp.toPx() }
    val left = (screenWidthPx - size) / 2f
    return Rect(left, top, left + size, top + size)
}

private fun estimatedProfileNameRect(
    density: Density,
    statusBarPx: Float,
    screenWidthPx: Float,
    name: String,
): Rect {
    val avatarTop = statusBarPx + with(density) { 58.dp.toPx() }
    val avatarSize = with(density) { 108.dp.toPx() }
    val top = avatarTop + avatarSize + with(density) { 10.dp.toPx() }
    val fontPx = with(density) { 22.sp.toPx() }
    val height = fontPx * 1.2f
    val width = name.length.coerceAtLeast(1) * fontPx * 0.62f
    val centerX = screenWidthPx / 2f
    return Rect(centerX - width / 2f, top, centerX + width / 2f, top + height)
}

package com.letify.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import com.letify.app.ui.screens.WallScreen
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
    // NOT `by` here on purpose: we need the raw State object itself to hand
    // to HomeScreen (see animationsActive below) so it stays a STABLE
    // reference across recompositions — only .value changes. `by` would
    // unwrap it to a plain Boolean at every read site, and passing that
    // Boolean into HomeScreen would force a full HomeScreen recompose each
    // time it flips (same bug as the old hideAvatarName — see placeholderState
    // above). tabSettled flips on EVERY tab switch, not just the hero
    // flight, so this one was actually the more frequent offender.
    val tabSettledState = remember { mutableStateOf(true) }

    // Rapid double-tap (open→close→open before the 520ms flight finishes)
    // used to change `current` again while the LaunchedEffect below was
    // still mid-`progress.animateTo`. Compose cancels + restarts that
    // LaunchedEffect on any `current` change, and the restart does
    // `progress.snapTo(0f)` unconditionally — discarding wherever the
    // interrupted flight visually was and yanking it back to 0 instantly.
    // That's the "быстро тыкать — всё мигает/дёргается": the screen jumps
    // to "arrived", then snaps back, then reflies. Routing every tab switch
    // through this instead of writing `state.currentTab` directly means a
    // tap that lands while `tabSettledState` is still false is simply
    // dropped — the in-flight animation is left to finish untouched. 520ms/
    // 320ms is short enough that an extra tap in that window reads as
    // "ignored", not as the app being unresponsive.
    val changeTab: (Tab) -> Unit = { tab -> if (tabSettledState.value) state.currentTab = tab }

    // Every overlay/sheet (Appearance, Goals, bottom sheets, ...) registers
    // its own BackHandler/PredictiveBackHandler, so swipe-back and the
    // hardware/gesture back button work there. The Home/Profile/Plan tab
    // pager itself never registered one — that's the whole bug: it's not
    // that back was broken on Profile specifically, it simply never existed
    // for the tab stack at all, on any tab. `enabled` is false on Home (there
    // is nothing to go "back" to) and false while any overlay is open (its
    // own handler owns back then — this one would otherwise fight it for the
    // same gesture). Routed through `changeTab` so a back-swipe while a
    // flight is still mid-animation gets the same debounce as a tap.
    BackHandler(enabled = overlayStack.isEmpty() && state.currentTab != Tab.Home) {
        changeTab(Tab.Home)
    }

    // Single identity for avatar+name (Home ⇄ Profile).
    // There is exactly ONE composable that paints them — at the root.
    // Home/Profile only keep empty size slots (placeholders). No copy, no hide-of-real.

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
                    onSettledChange = { tabSettledState.value = it },
                    modifier = Modifier.fillMaxSize(),
                    identity = { t ->
                        val density = LocalDensity.current
                        val statusBarPx = WindowInsets.statusBars.getTop(density).toFloat()
                        val screenW =
                            with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
                        val name = state.userName.ifBlank { "друг" }
                        // Measure the real glyph boxes so the flying label lands
                        // exactly where Home / Profile place it (no drift).
                        val measurer = rememberTextMeasurer()
                        // Keyed on `name` only. `t` is now `() -> Float`, read
                        // only inside UserIdentity's graphicsLayer{} blocks — so
                        // this whole lambda body (and therefore this remember)
                        // runs at flight start/end only, not 60×/sec per frame.
                        val homeLayout = remember(name) {
                            measurer.measure(
                                AnnotatedString(name),
                                TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                            )
                        }
                        val profileLayout = remember(name) {
                            measurer.measure(
                                AnnotatedString(name),
                                TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                            )
                        }
                        UserIdentity(
                            progress = t,
                            sourceAvatar = estimatedHomeAvatarRect(density, statusBarPx),
                            targetAvatar = estimatedProfileAvatarRect(density, statusBarPx, screenW),
                            sourceName = estimatedHomeNameRect(
                                density, statusBarPx,
                                homeLayout.size.width.toFloat(),
                                homeLayout.size.height.toFloat(),
                            ),
                            targetName = estimatedProfileNameRect(
                                density, statusBarPx, screenW,
                                profileLayout.size.width.toFloat(),
                                profileLayout.size.height.toFloat(),
                            ),
                            sourceFontSp = 19f,
                            targetFontSp = 22f,
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
                                onOpenProfile = { changeTab(Tab.Profile) },
                                onOpenPlan = { changeTab(Tab.Plan) },
                                onOpenMoments = { push(AddOverlay.Media) },
                                // Placeholder only — the one real identity is at the root.
                                hideAvatarName = usePlaceholder,
                                // Freeze the 4 IterateForever Lottie stickers while ANY
                                // tab transition is in flight (this was tracked via
                                // `tabSettled` already for the navbar blur but never
                                // reached Home) — they were invalidating every frame
                                // the whole time, fighting the hero flight for frame
                                // budget and reading as stutter during Home⇄Profile.
                                animationsActive = tabSettledState,
                            )
                            Tab.Nutrition -> {
                                androidx.compose.runtime.LaunchedEffect(Unit) {
                                    state.currentTab = Tab.Home
                                }
                                Box(Modifier.fillMaxSize())
                            }
                            Tab.Plan -> PlanScreen(
                                onBack = { changeTab(Tab.Home) },
                                onAddHabit = { push(AddOverlay.Habit()) },
                                onAddTask = { push(AddOverlay.Task()) },
                                onEditHabit = { id -> push(AddOverlay.Habit(id)) },
                                onEditTask = { id -> push(AddOverlay.Task(id)) },
                            )
                            Tab.Profile -> ProfileScreen(
                                onBack = { changeTab(Tab.Home) },
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
            if (u != AddOverlay.Weight && u != AddOverlay.Sleep) {
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
        // overlay. Weight and Sleep are BottomSheets (rendered outside this
        // branch) — wrapping them in RoundedSlideOverlay would run a full
        // page-slide transition BEFORE the sheet's own slide-up animation,
        // which reads as "the screen changes, then the sheet opens".
        overlay?.let { current ->
            if (current == AddOverlay.Weight) {
                AddWeightScreen(onBack = { pop() })
            } else if (current == AddOverlay.Sleep) {
                AddSleepScreen(onBack = { pop() })
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
        AddOverlay.Sleep -> {} // sleep is a bottom-sheet, handled elsewhere
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
        AddOverlay.Media -> WallScreen(
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
     * The single avatar+name identity. `t` is absolute: 0 = home layout,
     * 1 = profile layout. Drawn whenever Home or Profile owns the surface —
     * at rest and during the flight. Screens only reserve empty slots.
     */
    identity: (@Composable (t: () -> Float) -> Unit)? = null,
    content: @Composable (tab: Tab, usePlaceholder: State<Boolean>) -> Unit,
) {
    val visited = remember { mutableStateListOf<Tab>() }
    if (current !in visited) visited.add(current)

    var fromTab by remember { mutableStateOf(current) }
    var toTab by remember { mutableStateOf(current) }
    val progress = remember { Animatable(1f) }
    // Stable for the whole flight — never flip mid-animation.
    var avatarPairFlight by remember { mutableStateOf(false) }
    // Same idea as `progress`: a stable holder that HomeScreen/ProfileScreen
    // read ONLY inside their graphicsLayer{} (draw phase) for the avatar/name
    // alpha. Passing usePlaceholder as a plain Boolean parameter used to force
    // a full HomeScreen/ProfileScreen recomposition — every task/list/date
    // recalculated — at the exact moment it flips (flight start AND flight
    // end), which is exactly the "рывок в начале и в конце" jank: smooth
    // mid-flight (draw-phase only) but a real recompute hit on both edges.
    val placeholderState = remember { mutableStateOf(false) }

    LaunchedEffect(current) {
        if (current != toTab) {
            val prev = toTab
            val next = current
            val isProfilePair =
                (prev == Tab.Home && next == Tab.Profile) ||
                (prev == Tab.Profile && next == Tab.Home)
            fromTab = prev
            toTab = next
            avatarPairFlight = isProfilePair
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
            avatarPairFlight = false
        }
    }

    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        // Composition-stable frame of the transition (no progress.value read here).
        val pending = current != toTab
        val activeFrom = if (pending) toTab else fromTab
        val activeTo = if (pending) current else toTab
        val isAvatarPair =
            avatarPairFlight ||
                ((activeFrom == Tab.Home && activeTo == Tab.Profile) ||
                    (activeFrom == Tab.Profile && activeTo == Tab.Home))
        val settledHome = !isAvatarPair && activeFrom == Tab.Home && activeTo == Tab.Home
        val settledProfile = !isAvatarPair && activeFrom == Tab.Profile && activeTo == Tab.Profile
        val showIdentity = isAvatarPair || settledHome || settledProfile
        SideEffect { placeholderState.value = showIdentity }
        val activeDir =
            if (order.indexOf(activeTo).coerceAtLeast(0) >= order.indexOf(activeFrom).coerceAtLeast(0))
                1f else -1f

        // ONE composition path for every visited tab. Switching between the old
        // isAvatarPair / normal branches disposed Home's subtree on every open
        // (scroll reset + Lottie restart = cards jump + emoji flash). Keep the
        // same Box → content tree; only the graphicsLayer math changes.
        visited.forEach { tab ->
            val parked = tab != activeTo && tab != activeFrom
            val isProfileTab = tab == Tab.Profile
            val isHomeTab = tab == Tab.Home
            // Home/Profile always use the root identity painter while either is active.
            // (Actual on/off is read from the stable `placeholderState` below —
            // not recomputed here — so toggling it never recomposes the screens.)
            // z-order for the Home↔Profile hero: Profile is always the foreground
            // plate of the pair (full-width travel); Home only does the small
            // parallax shift. For other tab pairs, the arriving tab is on top.
            val z = when {
                isAvatarPair && isProfileTab -> 1f
                isAvatarPair && isHomeTab -> 0f
                tab == activeTo -> 1f
                else -> 0f
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(z)
                    .graphicsLayer {
                        // progress.value is read HERE (draw phase) — not in
                        // composition — so the heavy tab content below does NOT
                        // recompose every animation frame. That was the main lag.
                        val raw = progress.value
                        val p = if (pending && raw >= 0.999f) 0f else raw
                        val heroParallax = 0.28f
                        val dx = if (isAvatarPair && !parked) {
                            when {
                                tab == activeFrom && activeTo == Tab.Profile -> -p * heroParallax * w
                                tab == activeTo && activeTo == Tab.Profile -> (1f - p) * w
                                tab == activeFrom && activeTo == Tab.Home -> p * w
                                tab == activeTo && activeTo == Tab.Home -> -(1f - p) * heroParallax * w
                                else -> 0f
                            }
                        } else when {
                            // Home parked behind a SETTLED Profile is a special
                            // case: the hero flight only ever shifts Home by
                            // heroParallax*w (it never fully leaves, Profile
                            // just covers it) — so its resting/parked position
                            // must match that shifted spot, not the generic
                            // fully-off-screen `w` other unrelated tabs park at.
                            // Parking it at `w` here (its old value) meant that
                            // the instant a Profile→Home close begins, Home's
                            // dx had to jump from `w` to `-heroParallax*w` in a
                            // single frame — exactly when alpha also flips from
                            // 0 to 1, so the jump was fully visible as a pop/
                            // snap right at the start of every close. Home→
                            // Profile opens never had this problem: Home starts
                            // that flight already active at dx=0 (not parked),
                            // and Profile's OWN parked value already is `w`,
                            // matching its flight-start value exactly.
                            parked && tab == Tab.Home &&
                                activeFrom == Tab.Profile && activeTo == Tab.Profile ->
                                -heroParallax * w
                            parked -> w
                            tab == activeTo -> (1f - p) * activeDir * w
                            tab == activeFrom -> -p * activeDir * w
                            else -> w
                        }
                        translationX = dx
                        alpha = if (parked) 0f else 1f
                        clip = true
                    }
                    // Background AFTER graphicsLayer so it rides the same
                    // translated/clipped layer as the content (not a static plate).
                    .background(Letify.colors.bg),
            ) {
                content(tab, placeholderState)
            }
        }

        if (showIdentity && identity != null) {
            // progress.value is read only inside graphicsLayer{} in
            // IdentityFlightHost/UserIdentity — same draw-phase-only rule as
            // the tab content above — so neither this overlay nor the tab
            // screens recompose per animation frame.
            IdentityFlightHost(
                progress = progress,
                pending = pending,
                flying = isAvatarPair,
                activeToProfile = activeTo == Tab.Profile,
                settledProfile = settledProfile,
                settledHome = settledHome,
                identity = identity,
            )
        }
    }
}

/**
 * Emits absolute t ∈ [0,1] (0 = home, 1 = profile) as a DEFERRED reader,
 * not a Float. `identity` and everything below it must call the lambda only
 * from inside a graphicsLayer{} (draw phase) — never in composition.
 *
 * This used to read `progress.value` directly in the composable body and
 * pass the resolved Float down. That looked "isolated" (only this overlay
 * recomposed, not the tab screens), but it still meant a REAL recomposition
 * of the whole avatar+name subtree ~60×/sec for the full 520ms flight —
 * competing with the tab content's draw phase for the same choreographer
 * frame. On a loaded frame that recomposition work is what pushed the
 * screen-slide's own draw past the vsync deadline, which read as the
 * screen-open animation "дрожит" specifically during the flight (the two
 * are on the same frame, so a stall in one shows up as jitter in both).
 * Tab content already avoids this (progress.value is read inside its own
 * graphicsLayer{}, see CachedTabPager below) — this brings the identity
 * overlay to the same discipline. Now IdentityFlightHost/UserIdentity only
 * recompose when pending/flying/settledX actually change (flight start and
 * end — a couple of times, not every frame).
 */
@Composable
private fun IdentityFlightHost(
    progress: Animatable<Float, *>,
    pending: Boolean,
    flying: Boolean,
    activeToProfile: Boolean,
    settledProfile: Boolean,
    settledHome: Boolean,
    identity: @Composable (t: () -> Float) -> Unit,
) {
    // activeToProfile is composition-stable for the flight and correct even on
    // the pre-LaunchedEffect frame (where flightToProfile flag is still stale).
    val computeT: () -> Float = {
        val raw = progress.value
        val p = if (pending && raw >= 0.999f) 0f else raw
        when {
            settledProfile -> 1f
            settledHome -> 0f
            flying && activeToProfile -> p
            flying && !activeToProfile -> 1f - p
            activeToProfile -> 1f
            else -> 0f
        }
    }
    Box(Modifier.fillMaxSize().zIndex(20f)) {
        identity(computeT)
    }
}

@Composable
private fun UserIdentity(
    progress: () -> Float,
    sourceAvatar: Rect,
    targetAvatar: Rect,
    sourceName: Rect,
    targetName: Rect,
    sourceFontSp: Float,
    targetFontSp: Float,
    name: String,
    photoUrl: String?,
    letter: String,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val avatarBrush = Brush.linearGradient(
        listOf(Letify.colors.accent, LetifyColors.TilePink),
    )

    // Size is applied via scale from a fixed source size so we don't re-layout
    // the avatar every frame (only graphicsLayer transform updates).
    val srcSize = sourceAvatar.width.coerceAtLeast(1f)

    Box(
        Modifier
            .graphicsLayer {
                // `progress()` is called HERE — inside the draw-phase lambda —
                // not above in the composable body. That's the whole fix: this
                // Box (and the Text below) no longer recompose on every flight
                // frame, only the layer's transform is re-drawn. See the
                // IdentityFlightHost doc comment for why that recomposition was
                // the actual source of the "screen trembles during the flight".
                val t = progress().coerceIn(0f, 1f)
                val avatarRect = lerp(sourceAvatar, targetAvatar, t)
                val scale = avatarRect.width / srcSize
                // Anchor at the interpolated top-left, then scale about top-left
                // so the circle grows toward the profile size in place.
                translationX = avatarRect.left
                translationY = avatarRect.top
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .size(with(density) { sourceAvatar.width.toDp() })
            .clip(CircleShape)
            .background(avatarBrush, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            color = Color.White,
            fontSize = with(density) { (sourceAvatar.width * 0.38f).toSp() },
            fontWeight = FontWeight.Bold,
        )
        if (!photoUrl.isNullOrBlank()) {
            // IMPORTANT: keep keying this on photoUrl only, never on progress/t.
            // This branch no longer recomposes per-frame (t is now read only
            // inside the graphicsLayer{} above), but if a future change ever
            // threads t back into composition here, an inline ImageRequest
            // built fresh each time would regress: Coil has no equals() on
            // ImageRequest, so every recomposition would look like a brand-new
            // model and the painter would flip Loading→Success repeatedly —
            // that flicker was the original "мигает" bug this remember() fixed.
            // The SAME request instance survives the whole flight; Coil keeps
            // the already-resolved painter and just redraws it under the
            // animated graphicsLayer transform.
            val avatarRequest = remember(context, photoUrl) {
                ImageRequest.Builder(context).data(photoUrl).crossfade(false).build()
            }
            AsyncImage(
                model = avatarRequest,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }

    // Name: pin the TOP-LEFT of the glyph box to the lerped origin.
    // Endpoints are measured with TextMeasurer against the real Home/Profile
    // styles, so at t=0 and t=1 the label sits exactly where the screen
    // would draw it. No fixed-width box mid-flight (that was clipping /
    // shifting the baseline when the interpolated width disagreed with the
    // live fontSize).
    //
    // IMPORTANT: fontSize is FIXED at sourceFontSp here — it is NOT animated
    // via the `fontSize` parameter. That used to be `fontSize = fontSp.sp`
    // with `fontSp` interpolating every frame, which meant Compose had to
    // re-shape the glyphs (real text layout via the platform's text engine)
    // on every single frame of the flight — a genuinely more expensive
    // operation than a transform, unlike the avatar (which was already
    // transform-scaled, not re-measured). That per-frame reshape is what
    // read as "дрожит, не плавная" over the whole flight, as opposed to the
    // one-off hitches already fixed elsewhere. Growing the SAME fixed-size
    // glyph layout via graphicsLayer scale (anchored at the same top-left
    // origin as the translation) is a pure GPU transform — no re-shaping —
    // and lands on the same visual size at t=1 as a native targetFontSp
    // layout would.
    Text(
        name,
        color = Letify.colors.text,
        fontSize = sourceFontSp.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.graphicsLayer {
            // Same deferred-read fix as the avatar Box above: t/rect/scale are
            // resolved HERE, in draw phase, so this Text never recomposes
            // (and never re-shapes glyphs) on animation frames — only its
            // layer transform is redrawn.
            val t = progress().coerceIn(0f, 1f)
            val nameRect = lerp(sourceName, targetName, t)
            val fontSp = sourceFontSp + (targetFontSp - sourceFontSp) * t
            val nameScale = fontSp / sourceFontSp
            translationX = nameRect.left
            translationY = nameRect.top
            scaleX = nameScale
            scaleY = nameScale
            transformOrigin = TransformOrigin(0f, 0f)
        },
    )
}

// Layout constants mirrored from HomeScreen / ProfileScreen — do not drift.
private fun estimatedHomeAvatarRect(density: Density, statusBarPx: Float): Rect {
    // Column top pad 6 + row vertical pad 10 = 16; horizontal pad 20; size 34
    val left = with(density) { 20.dp.toPx() }
    val top = statusBarPx + with(density) { 16.dp.toPx() }
    val size = with(density) { 34.dp.toPx() }
    return Rect(left, top, left + size, top + size)
}

private fun estimatedHomeNameRect(
    density: Density,
    statusBarPx: Float,
    textWidthPx: Float,
    textHeightPx: Float,
): Rect {
    val avatarTop = statusBarPx + with(density) { 16.dp.toPx() }
    val avatarSize = with(density) { 34.dp.toPx() }
    val centerY = avatarTop + avatarSize / 2f
    // 20 pad + 34 avatar + 12 spacer — matches HomeScreen header Row.
    val left = with(density) { 66.dp.toPx() }
    return Rect(
        left,
        centerY - textHeightPx / 2f,
        left + textWidthPx,
        centerY + textHeightPx / 2f,
    )
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
    textWidthPx: Float,
    textHeightPx: Float,
): Rect {
    val avatarTop = statusBarPx + with(density) { 58.dp.toPx() }
    val avatarSize = with(density) { 108.dp.toPx() }
    // Avatar bottom + 10.dp gap — matches ProfileScreen name Box padding(top=10.dp).
    val top = avatarTop + avatarSize + with(density) { 10.dp.toPx() }
    val centerX = screenWidthPx / 2f
    return Rect(
        centerX - textWidthPx / 2f,
        top,
        centerX + textWidthPx / 2f,
        top + textHeightPx,
    )
}


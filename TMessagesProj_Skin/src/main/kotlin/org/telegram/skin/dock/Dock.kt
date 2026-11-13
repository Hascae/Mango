package org.telegram.skin.dock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import org.telegram.skin.theme.Skin
import org.telegram.skin.theme.shadows
import org.telegram.skin.ui.Avatar
import org.telegram.skin.ui.FittedText
import org.telegram.skin.ui.GlyphIcon
import org.telegram.skin.ui.SkinText
import org.telegram.skin.ui.Groove
import org.telegram.skin.ui.Knob
import org.telegram.skin.ui.metal
import org.telegram.skin.ui.porcelain
import kotlin.math.roundToInt

/** From this width on the dock stands as a rail along the start edge instead. */
internal val RailBreakpoint = 600.dp

internal val RailHeight = 64.dp
private val RailWidth = 76.dp
private val KnobSize = 50.dp
private val DockGap = 8.dp

/**
 * The aluminium dock and everything it grows into, drawn over the whole
 * screen. Only the dock itself (and, while open, the backdrop) takes touches,
 * so the page underneath works as usual.
 *
 * [restingPick] draws the account fan with that card lifted, as while a finger
 * rests on it; it exists for previews, the app never sets it.
 */
@Composable
fun DockOverlay(state: DockState, actions: DockActions, modifier: Modifier = Modifier, restingPick: Int = -1) {
    val c = Skin.colors
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val fan = remember { FanTracker() }
    // Lets a still image show the fan as it looks under a resting finger.
    if (restingPick >= 0 && state.mode == DockMode.Accounts) {
        fan.armed = true
        fan.picked = restingPick
    }

    // Leaving the 起点 panel takes the keyboard and the query with it.
    LaunchedEffect(state.mode) {
        if (state.mode != DockMode.Create) {
            focus.clearFocus()
            keyboard?.hide()
            if (state.query.isNotEmpty()) {
                state.query = ""
                actions.onQuery("")
            }
        }
        if (state.mode != DockMode.Accounts) fan.reset()
    }

    val open: (DockMode) -> Unit = { mode ->
        if (state.mode != mode) {
            state.mode = mode
            if (mode != DockMode.Rail) state.collapsed = false
            actions.onModeChanged(mode)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val vertical = maxWidth >= RailBreakpoint
        val top = with(density) { state.topInset.toDp() }
        val bottom = with(density) { maxOf(state.bottomInset, state.imeInset).toDp() }
        val start = with(density) { state.startInset.toDp() }

        // A thick cool fog by day and a near-black veil at night, so nothing
        // behind ghosts through the panels and readouts laid over it.
        val veil by animateFloatAsState(if (state.isOpen) 1f else 0f, label = "veil")
        if (veil > 0f) {
            val scrim = if (c.isDark) c.chat.copy(alpha = 0.9f) else c.page.copy(alpha = 0.95f)
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = veil }
                    .background(scrim)
                    .then(if (state.isOpen) Modifier.pointerInput(Unit) { detectTapGestures { open(DockMode.Rail) } } else Modifier),
            )
        }

        if (state.mode == DockMode.Accounts) {
            AccountFan(state, actions, fan, vertical, top, open)
        }

        val railRoom = maxHeight - top - bottom - 24.dp
        val panelRoom = if (vertical) maxHeight - top - bottom - 24.dp else maxHeight - top - bottom - RailHeight - DockGap - 40.dp
        if (vertical) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = start + 12.dp, top = top + 12.dp, bottom = bottom + 12.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                AnimatedVisibility(
                    visible = state.shown,
                    enter = slideInHorizontally { -it } + fadeIn(),
                    exit = slideOutHorizontally { -it } + fadeOut(),
                ) {
                    DockSurface(state, actions, fan, vertical = true, panelRoom = panelRoom, railRoom = railRoom, open = open)
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = Skin.space.dockInset, end = Skin.space.dockInset, bottom = bottom + DockGap),
                contentAlignment = Alignment.BottomCenter,
            ) {
                AnimatedVisibility(
                    visible = state.shown,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    DockSurface(state, actions, fan, vertical = false, panelRoom = panelRoom, railRoom = 0.dp, open = open)
                }
            }
        }
    }
}

@Composable
private fun DockSurface(
    state: DockState,
    actions: DockActions,
    fan: FanTracker,
    vertical: Boolean,
    panelRoom: Dp,
    railRoom: Dp,
    open: (DockMode) -> Unit,
) {
    val c = Skin.colors
    val panel = state.mode == DockMode.Create || state.mode == DockMode.Arrange
    val collapsed = state.collapsed && state.mode == DockMode.Rail && !vertical
    val radius by animateDpAsState(
        when {
            panel -> 30.dp
            collapsed -> 22.dp
            else -> 26.dp
        },
        label = "radius",
    )
    val shape = RoundedCornerShape(radius)
    // The shadow sits outside the size animation, which clips to its bounds.
    val surface = Modifier
        .shadows(Skin.shadows.floating, shape)
        .animateContentSize()
        .metal(c.metal, shape)
        .onGloballyPositioned { fan.dock = it.boundsInRoot() }

    if (vertical) {
        Row(surface) {
            VerticalRail(state, actions, fan, railRoom, open)
            AnimatedVisibility(panel, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                Row {
                    Groove(vertical = true, Modifier.padding(vertical = 14.dp))
                    Box(Modifier.width(360.dp)) { Panels(state, actions, panelRoom) }
                }
            }
        }
    } else {
        Column(if (collapsed) surface else Modifier.fillMaxWidth().then(surface)) {
            AnimatedVisibility(panel, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    Panels(state, actions, panelRoom)
                    Groove(vertical = false, Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp))
                }
            }
            if (collapsed) CollapsedRail(state, open) else HorizontalRail(state, actions, fan, open)
        }
    }
}

@Composable
private fun Panels(state: DockState, actions: DockActions, room: Dp) {
    when (state.mode) {
        DockMode.Create -> CreatePanel(state, actions, room)
        DockMode.Arrange -> ArrangePanel(state, actions, room)
        else -> Unit
    }
}

@Composable
private fun HorizontalRail(state: DockState, actions: DockActions, fan: FanTracker, open: (DockMode) -> Unit) {
    Row(Modifier.height(RailHeight).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val count = state.tabs.size
            if (count > 0) {
                val tabWidth = maxWidth / count
                Inlay(state, Modifier.width(tabWidth).fillMaxHeight().padding(horizontal = 2.dp, vertical = 6.dp)) { position ->
                    IntOffset((position * tabWidth.toPx()).roundToInt(), 0)
                }
                Row(Modifier.fillMaxSize()) {
                    state.tabs.forEachIndexed { index, tab ->
                        TabKey(state, actions, fan, index, tab, labels = true, open = open, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
        Groove(vertical = true, Modifier.height(34.dp).padding(horizontal = 6.dp))
        DockKnob(state, KnobSize, open)
    }
}

@Composable
private fun VerticalRail(state: DockState, actions: DockActions, fan: FanTracker, room: Dp, open: (DockMode) -> Unit) {
    Column(
        Modifier.width(RailWidth).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val count = state.tabs.size
        if (count > 0) {
            // Keys shrink to fit a short screen, and drop their labels when
            // there is no longer room for them.
            val tabHeight = min(60.dp, max(40.dp, (room - KnobSize - 40.dp) / count))
            val labels = tabHeight >= 52.dp
            Box(Modifier.height(tabHeight * count).fillMaxWidth()) {
                Inlay(state, Modifier.fillMaxWidth().height(tabHeight).padding(horizontal = 6.dp, vertical = 2.dp)) { position ->
                    IntOffset(0, (position * tabHeight.toPx()).roundToInt())
                }
                Column(Modifier.fillMaxSize()) {
                    state.tabs.forEachIndexed { index, tab ->
                        TabKey(state, actions, fan, index, tab, labels = labels, open = open, modifier = Modifier.fillMaxWidth().height(tabHeight))
                    }
                }
            }
        }
        Groove(vertical = false, Modifier.width(34.dp).padding(vertical = 8.dp))
        DockKnob(state, KnobSize, open)
    }
}

/** The porcelain under the current key. It glides with the pages as they are swiped. */
@Composable
private fun Inlay(
    state: DockState,
    modifier: Modifier,
    offset: Density.(position: Float) -> IntOffset,
) {
    val p = Skin.colors.porcelain
    val last = (state.tabs.size - 1).coerceAtLeast(0).toFloat()
    Box(
        modifier
            // Read in the layout phase only, so a swipe moves the glaze
            // without recomposing the keys.
            .offset { offset(state.position.coerceIn(0f, last)) }
            .porcelain(p, Skin.shapes.inlay),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabKey(
    state: DockState,
    actions: DockActions,
    fan: FanTracker,
    index: Int,
    tab: DockTab,
    labels: Boolean,
    open: (DockMode) -> Unit,
    modifier: Modifier,
) {
    val c = Skin.colors
    val selected = state.position.roundToInt() == index
    val tint by animateColorAsState(if (selected) c.porcelain.text else c.textSecondary, label = "tint")
    val isMe = tab.avatar != null
    var center by remember { mutableStateOf(Rect.Zero) }

    val tap = {
        if (state.isOpen) open(DockMode.Rail)
        actions.onTab(index)
    }
    val gesture = when {
        isMe -> Modifier.meKeyGestures(state, actions, fan, index, open)
        tab.arrangeable -> Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = tap,
            onLongClick = { open(DockMode.Arrange) },
        )
        else -> Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = tap,
            onLongClick = { actions.onTabMenu(index, center.center.x, center.center.y) },
        )
    }

    Column(
        modifier
            .onGloballyPositioned { center = it.boundsInRoot() }
            .then(gesture)
            .semantics(mergeDescendants = true) {
                contentDescription = tab.description
                role = Role.Tab
                this.selected = selected
                when {
                    isMe -> customActions = listOf(CustomAccessibilityAction(state.strings.switchAccountAction) { open(DockMode.Accounts); true })
                    tab.arrangeable -> customActions = listOf(CustomAccessibilityAction(state.strings.arrangeAction) { open(DockMode.Arrange); true })
                }
            }
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (tab.avatar != null) {
                Avatar(tab.avatar, 22.dp, shape = RoundedCornerShape(8.dp))
            } else {
                GlyphIcon(tab.glyph, tint, size = 22.dp)
            }
        }
        if (labels) {
            val style = Skin.type.micro.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            LabelWithFigure(
                label = { FittedText(tab.label, style, tint) },
                figure = tab.readout?.let { readout ->
                    { SkinText(readout, Skin.type.figureSmall.copy(fontSize = Skin.type.micro.fontSize * 0.92f), tint.copy(alpha = 0.8f), maxLines = 1) }
                },
            )
        }
    }
}

/**
 * A key's name with a figure after it. The name always keeps its room; when
 * both do not fit (a very large system font) the figure is left out whole
 * rather than cut to an ellipsis. Screen readers still hear it in the key's
 * description.
 */
@Composable
private fun LabelWithFigure(label: @Composable () -> Unit, figure: (@Composable () -> Unit)?) {
    Layout(
        content = {
            label()
            figure?.invoke()
        },
    ) { measurables, constraints ->
        val gap = 3.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0)
        val name = measurables[0].measure(loose)
        val number = measurables.getOrNull(1)?.measure(loose.copy(maxWidth = Constraints.Infinity))
        val fits = number != null && name.width + gap + number.width <= constraints.maxWidth
        val width = if (fits) name.width + gap + number!!.width else name.width
        val height = maxOf(name.height, if (fits) number!!.height else 0)
        layout(width, height) {
            name.placeRelative(0, height - name.height)
            if (fits) number!!.placeRelative(name.width + gap, height - number.height)
        }
    }
}

/** The folded dock: just the current key and the knob, centred. */
@Composable
private fun CollapsedRail(state: DockState, open: (DockMode) -> Unit) {
    val c = Skin.colors
    val tab = state.tabs.getOrNull(state.position.roundToInt().coerceIn(0, (state.tabs.size - 1).coerceAtLeast(0))) ?: return
    Row(Modifier.height(52.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .height(40.dp)
                .widthIn(min = 110.dp, max = 220.dp)
                .porcelain(c.porcelain, Skin.shapes.inlayCollapsed)
                .clickable { state.collapsed = false }
                .semantics(mergeDescendants = true) { contentDescription = tab.description; role = Role.Button }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (tab.avatar != null) {
                Avatar(tab.avatar, 20.dp, shape = RoundedCornerShape(7.dp))
            } else {
                GlyphIcon(tab.glyph, c.porcelain.text, size = 20.dp)
            }
            FittedText(tab.label, Skin.type.caption.copy(fontSize = Skin.type.label.fontSize, fontWeight = FontWeight.SemiBold), c.porcelain.text, Modifier.weight(1f, fill = false))
            if (tab.readout != null) {
                FittedText(tab.readout, Skin.type.figureSmall, c.porcelain.text.copy(alpha = 0.8f))
            }
        }
        Groove(vertical = true, Modifier.height(26.dp).padding(horizontal = 6.dp))
        DockKnob(state, 40.dp, open)
    }
}

@Composable
private fun DockKnob(state: DockState, size: Dp, open: (DockMode) -> Unit) {
    val turn by animateFloatAsState(if (state.isOpen) 1f else 0f, label = "turn")
    Knob(
        turn = turn,
        size = size,
        modifier = Modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                open(if (state.isOpen) DockMode.Rail else DockMode.Create)
            }
            .semantics {
                contentDescription = if (state.isOpen) state.strings.close else state.strings.create
                role = Role.Button
            },
    )
}

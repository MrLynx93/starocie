package pl.starocie.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * The grey bars that stand in for a screen's numbers while the ledger is still
 * being read, and the one band of light that crosses all of them.
 *
 * The band belongs to the *screen*, not to any one bar: a placeholder shows the
 * part of it that happens to be passing over, so a home screen full of them reads
 * as one surface being read rather than a dozen things flickering out of step.
 */

/** How long one crossing takes, end to end. */
private const val SweepMillis = 1_600L

/** The band's width, across the direction it travels. */
private val BandWidth = 220.dp

/**
 * How far it travels, which is how much of [SweepMillis] the crossing itself takes.
 *
 * It has to cover a phone's width plus [Lean]'s share of its height, or the bottom
 * corner is never lit; anything past that is the rest between passes. This lands
 * around a quarter rest on a small phone and almost none on the largest, which is
 * the one direction the trade should go — a big screen has further to carry the
 * light, so it can spare the pause.
 */
private val Travel = 950.dp

/**
 * How far the band leans off vertical. A straight-down band would light a whole row
 * at once and the sweep would read as a wipe; this is what makes it arrive at the
 * bottom of the screen a moment after the top.
 */
private const val Lean = 0.35f

/**
 * Where the band is in its crossing, as 0..1.
 *
 * Taken from the animation clock rather than an `InfiniteTransition`, because a
 * transition counts from the frame it was composed on: two bars composed a frame
 * apart would each run their own band. The frame time is the same number
 * everywhere, so every bar on screen agrees about where the light is, whenever it
 * happened to appear.
 */
@Composable
private fun sweepPhase(): State<Float> = produceState(0f) {
    while (true) {
        withInfiniteAnimationFrameMillis { frame ->
            value = (frame % SweepMillis) / SweepMillis.toFloat()
        }
    }
}

/**
 * Fills whatever it is applied to with the passing band.
 *
 * Both the phase and this element's position are read inside the draw lambda, so a
 * moving band costs a redraw rather than a recomposition.
 */
@Composable
fun Modifier.shimmer(): Modifier {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    // Which way the band brightens depends on which palette is up, because the ink
    // is dark on the light one and light on the dark one: more of it lightens a bar
    // in the dark and darkens it in the light. Getting this the wrong way round
    // gives the dark theme a shadow sweeping past instead of a light. The palette in
    // play is what says which — never `isSystemInDarkTheme()`, which the app as a
    // whole does not consult.
    val onDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val base = ink.copy(alpha = if (onDark) 0.15f else 0.20f)
    // Never far enough to leave nothing behind: a bar that dissolves as the light
    // reaches it reads as a gap opening rather than as something being read.
    val highlight = ink.copy(alpha = if (onDark) 0.30f else 0.08f)
    val phase = sweepPhase()

    // Window coordinates are the shared space that makes one band out of many
    // separately drawn bars: each subtracts its own position from the band's.
    var origin by remember { mutableStateOf(Offset.Zero) }

    return onGloballyPositioned { origin = it.positionInWindow() }
        .drawBehind {
            val band = BandWidth.toPx()
            val head = phase.value * Travel.toPx() - band
            val start = Offset(head - origin.x, -origin.y)
            drawRect(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = start,
                    end = Offset(start.x + band, start.y + band * Lean),
                ),
            )
        }
}

/**
 * A bar standing in for one line of text set in [style].
 *
 * It takes exactly the height that line will take, so nothing below it moves when
 * the number arrives — a skeleton that shoves the screen down on arrival is worse
 * than the blank it replaced. [fraction] is how much of the width the bar covers;
 * varying it down a column is what keeps a card from looking like a form.
 */
@Composable
fun SkeletonLine(
    fraction: Float,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val line = with(density) { style.lineHeight.toDp() }
    val bar = with(density) { style.fontSize.toDp() } * 0.72f
    SkeletonLine(fraction = fraction, line = line, bar = bar, modifier = modifier)
}

/** The same bar, for the places sized in dp rather than by a text style. */
@Composable
fun SkeletonLine(
    fraction: Float,
    line: Dp,
    bar: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxWidth().height(line),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(bar)
                .clip(RoundedCornerShape(bar / 3))
                .shimmer(),
        )
    }
}

/**
 * Whether to draw a skeleton at all, given that the ledger is still [loading].
 *
 * Firestore answers from its own cache on every launch but the first, so the wait
 * is usually a frame or two — and a skeleton that appears and vanishes inside
 * [after] is a flicker rather than a state. Waiting that long before showing one
 * costs nothing on a cold start, where the wait is a network round-trip.
 */
@Composable
fun rememberSkeletonVisible(
    loading: Boolean,
    after: Duration = 150.milliseconds,
): Boolean {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(loading) {
        if (!loading) {
            visible = false
        } else {
            delay(after)
            visible = true
        }
    }
    return visible
}

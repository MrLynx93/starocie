package pl.starocie.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

/**
 * The app's own mark — the market stall from the launcher icon — drawn rather than
 * loaded.
 *
 * It is the same geometry as `icon/starocie-icon.svg`, in the same 100-unit space,
 * so the two can be kept in step by eye. Drawing it means no image resource, no
 * density set to keep in sync and no platform difference: it is sharp at any size
 * on both phones, which a 48 dp PNG blown up to fill a sign-in screen would not be.
 */
@Composable
fun StallMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 100f
        translate(
            left = (size.width - 100f * unit) / 2f,
            top = (size.height - 100f * unit) / 2f,
        ) {
            scale(unit, unit, pivot = Offset.Zero) { drawStall() }
        }
    }
}

private val Stripes = listOf(
    Color(0xFF3D6E9C),
    Color(0xFF2F8F7F),
    Color(0xFFE9A13B),
    Color(0xFFD9534F),
)

private val LegBrown = Color(0xFF6B4A2E)

/** The peaked roof, its concave sweeps down to the eaves. */
private val roof = Path().apply {
    moveTo(8f, 50f)
    cubicTo(20f, 46f, 27f, 22f, 39f, 18f)
    lineTo(61f, 18f)
    cubicTo(73f, 22f, 80f, 46f, 92f, 50f)
    close()
}

/**
 * The hanging band under the eaves. The scallops are what make it a stall — with a
 * straight edge the whole shape reads as a table.
 */
private val valance = Path().apply {
    moveTo(8f, 50f)
    lineTo(92f, 50f)
    lineTo(92f, 56f)
    quadraticTo(81.5f, 64f, 71f, 56f)
    quadraticTo(60.5f, 64f, 50f, 56f)
    quadraticTo(39.5f, 64f, 29f, 56f)
    quadraticTo(18.5f, 64f, 8f, 56f)
    close()
}

private fun DrawScope.drawStall() {
    drawRoundRect(LegBrown, Offset(15f, 58f), Size(5f, 30f), CornerRadius(2.5f))
    drawRoundRect(LegBrown, Offset(80f, 58f), Size(5f, 30f), CornerRadius(2.5f))

    // The stripes are full-height bands clipped to the canopy, which is what lets
    // one set of colours run across two shapes without the seams being redrawn.
    clipPath(roof) { stripes(top = 14f, height = 40f) }
    clipPath(valance) { stripes(top = 50f, height = 16f) }
}

private fun DrawScope.stripes(top: Float, height: Float) {
    Stripes.forEachIndexed { index, colour ->
        drawRect(colour, Offset(8f + 21f * index, top), Size(21f, height))
    }
}

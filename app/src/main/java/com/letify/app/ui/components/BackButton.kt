package com.letify.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A plain "<" chevron, drawn directly with Canvas instead of loaded from an
 * SVG asset. Every screen used to reference the icon by name
 * ("alt-arrow-left-outline") through the Solar icon pipeline, but that gave
 * inconsistent results across call sites — Plan even fell back to a raw "←"
 * text glyph instead of an icon at all. This is the single source of truth
 * for the back-navigation glyph: same shape, same weight, everywhere.
 */
@Composable
fun BackChevron(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    strokeWidth: Dp = 2.3.dp,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.62f, h * 0.10f)
            lineTo(w * 0.27f, h * 0.5f)
            lineTo(w * 0.62f, h * 0.90f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

/** [BackChevron] wrapped in the standard 44dp no-ripple tap target. */
@Composable
fun BackButton(
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
    tapSize: Dp = 44.dp,
    glyphSize: Dp = 22.dp,
) {
    NoFeedbackButton(onClick = onClick, modifier = modifier.size(tapSize)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BackChevron(tint = tint, size = glyphSize)
        }
    }
}

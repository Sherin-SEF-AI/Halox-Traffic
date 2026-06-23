package com.haloxtraffic.feature.capture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.haloxtraffic.core.designsystem.theme.HaloxTheme
import com.haloxtraffic.core.model.BoundingBox

/**
 * Draws detector boxes over the camera preview (§12.2). Boxes arrive in upright-frame normalised
 * coordinates; since `Preview` and `ImageAnalysis` share a 16:9 selector the mapping to the overlay is
 * direct. Colour follows the operational palette — plates are amber, everything else ink — with red
 * reserved for committed violations (Phase 3).
 */
@Composable
fun DetectionOverlay(
    boxes: List<BoundingBox>,
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = HaloxTheme.colors
    val textMeasurer: TextMeasurer = rememberTextMeasurer()
    val labelStyle = HaloxTheme.typography.dataSmall

    Canvas(modifier.fillMaxSize()) {
        boxes.forEach { box ->
            val left = box.left * size.width
            val top = box.top * size.height
            val w = box.width * size.width
            val h = box.height * size.height

            val labelText = labels.getOrNull(box.classId) ?: box.classId.toString()
            val accent = if (labelText == "plate") colors.degraded else colors.confirmed

            drawRect(
                color = accent,
                topLeft = Offset(left, top),
                size = Size(w, h),
                style = Stroke(width = 2.dp.toPx()),
            )

            val tag = "$labelText ${(box.score * 100).toInt()}"
            val measured = textMeasurer.measure(tag, labelStyle)
            drawRect(
                color = colors.void.copy(alpha = 0.7f),
                topLeft = Offset(left, (top - measured.size.height).coerceAtLeast(0f)),
                size = Size(measured.size.width.toFloat(), measured.size.height.toFloat()),
            )
            drawText(
                textMeasurer = textMeasurer,
                text = tag,
                style = labelStyle.copy(color = accent),
                topLeft = Offset(left, (top - measured.size.height).coerceAtLeast(0f)),
            )
        }
    }
}

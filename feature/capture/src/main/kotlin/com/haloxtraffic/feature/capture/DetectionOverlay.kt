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
import com.haloxtraffic.feature.violations.ActiveViolation

/**
 * Draws detector boxes + committed-violation boxes over the camera preview (§12.2). Boxes arrive in
 * upright-frame normalised coordinates; since `Preview` and `ImageAnalysis` share a 16:9 selector the
 * mapping is direct. Colour follows the operational palette — plates amber, other detections green,
 * and committed violations red (thicker, labelled with the violation type).
 */
@Composable
fun DetectionOverlay(
    boxes: List<BoundingBox>,
    labels: List<String>,
    violations: List<ActiveViolation>,
    modifier: Modifier = Modifier,
) {
    val colors = HaloxTheme.colors
    val textMeasurer: TextMeasurer = rememberTextMeasurer()
    val labelStyle = HaloxTheme.typography.dataSmall

    Canvas(modifier.fillMaxSize()) {
        // Detections.
        boxes.forEach { box ->
            val labelText = labels.getOrNull(box.classId) ?: box.classId.toString()
            val accent = if (labelText == "plate") colors.degraded else colors.confirmed
            drawBox(box, accent, 2.dp.toPx(), "$labelText ${(box.score * 100).toInt()}", textMeasurer, labelStyle, colors.void)
        }
        // Committed violations on top, red and thicker.
        violations.forEach { v ->
            val label = v.types.joinToString("+") { it.displayName }
            drawBox(v.box, colors.violation, 3.dp.toPx(), label, textMeasurer, labelStyle, colors.void)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBox(
    box: BoundingBox,
    accent: androidx.compose.ui.graphics.Color,
    strokeWidth: Float,
    tag: String,
    textMeasurer: TextMeasurer,
    labelStyle: androidx.compose.ui.text.TextStyle,
    scrim: androidx.compose.ui.graphics.Color,
) {
    val left = box.left * size.width
    val top = box.top * size.height
    drawRect(
        color = accent,
        topLeft = Offset(left, top),
        size = Size(box.width * size.width, box.height * size.height),
        style = Stroke(width = strokeWidth),
    )
    val measured = textMeasurer.measure(tag, labelStyle)
    val labelTop = (top - measured.size.height).coerceAtLeast(0f)
    drawRect(
        color = scrim.copy(alpha = 0.7f),
        topLeft = Offset(left, labelTop),
        size = Size(measured.size.width.toFloat(), measured.size.height.toFloat()),
    )
    drawText(textMeasurer = textMeasurer, text = tag, style = labelStyle.copy(color = accent), topLeft = Offset(left, labelTop))
}

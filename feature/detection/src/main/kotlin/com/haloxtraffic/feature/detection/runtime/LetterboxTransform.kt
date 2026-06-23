package com.haloxtraffic.feature.detection.runtime

import com.haloxtraffic.core.model.BoundingBox

/**
 * Records how an upright frame was scaled + padded into the square model input, so detector boxes
 * (normalised to the square) can be mapped back to the original upright frame (also normalised 0..1)
 * for the overlay. Pure and unit-testable.
 *
 * @param inputSize square model edge in px.
 * @param contentW drawn content width in px within the square (== uprightW * scale).
 * @param contentH drawn content height in px within the square.
 * @param offsetX left letterbox padding in px.
 * @param offsetY top letterbox padding in px.
 */
data class LetterboxTransform(
    val inputSize: Int,
    val contentW: Float,
    val contentH: Float,
    val offsetX: Float,
    val offsetY: Float,
) {
    /** Map one box from square-normalised model space → upright-frame-normalised space. */
    fun invert(box: BoundingBox): BoundingBox {
        return box.copy(
            left = nx(box.left),
            top = ny(box.top),
            right = nx(box.right),
            bottom = ny(box.bottom),
        )
    }

    fun invert(boxes: List<BoundingBox>): List<BoundingBox> = boxes.map(::invert)

    /** Map one box from upright-frame-normalised → square-normalised (for cropping the model bitmap). */
    fun forward(box: BoundingBox): BoundingBox = box.copy(
        left = fx(box.left), top = fy(box.top), right = fx(box.right), bottom = fy(box.bottom),
    )

    private fun nx(n: Float): Float = (((n * inputSize) - offsetX) / contentW).coerceIn(0f, 1f)
    private fun ny(n: Float): Float = (((n * inputSize) - offsetY) / contentH).coerceIn(0f, 1f)
    private fun fx(n: Float): Float = ((n * contentW + offsetX) / inputSize).coerceIn(0f, 1f)
    private fun fy(n: Float): Float = ((n * contentH + offsetY) / inputSize).coerceIn(0f, 1f)

    companion object {
        /** Compute the aspect-preserving letterbox of an [uprightW]×[uprightH] frame into [inputSize]². */
        fun forFrame(uprightW: Int, uprightH: Int, inputSize: Int): LetterboxTransform {
            val scale = inputSize / maxOf(uprightW, uprightH).toFloat()
            val contentW = uprightW * scale
            val contentH = uprightH * scale
            return LetterboxTransform(
                inputSize = inputSize,
                contentW = contentW,
                contentH = contentH,
                offsetX = (inputSize - contentW) / 2f,
                offsetY = (inputSize - contentH) / 2f,
            )
        }
    }
}

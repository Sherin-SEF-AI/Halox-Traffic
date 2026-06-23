package com.haloxtraffic.feature.anpr

/**
 * Best-frame selection metric (§7B): variance of the Laplacian over a grayscale crop. Motion blur is
 * the dominant ANPR failure mode, so the sharpest crops of the same plate are preferred for OCR. Pure
 * and unit-testable — a uniform image scores ~0, a sharp edge scores high.
 */
object SharpnessScorer {

    /**
     * @param gray row-major grayscale intensities (0..255), length == [width] * [height].
     * @return variance of the Laplacian; higher = sharper.
     */
    fun varianceOfLaplacian(gray: FloatArray, width: Int, height: Int): Double {
        require(gray.size == width * height) { "gray size != width*height" }
        if (width < 3 || height < 3) return 0.0

        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                // 4-neighbour Laplacian kernel.
                val lap = (gray[i - 1] + gray[i + 1] + gray[i - width] + gray[i + width]) - 4f * gray[i]
                sum += lap
                sumSq += lap.toDouble() * lap
                n++
            }
        }
        if (n == 0) return 0.0
        val mean = sum / n
        return sumSq / n - mean * mean
    }
}

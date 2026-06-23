package com.haloxtraffic.feature.violations.tracking

/**
 * Constant-velocity Kalman filter for a 2D point (§4). State = [x, y, vx, vy], measurement = [x, y],
 * dt = 1 frame. Used to smooth a track's centre and estimate its velocity for direction-of-travel.
 * Pure and unit-testable.
 *
 * @param processNoise Q scale — higher tracks abrupt motion, lower is smoother.
 * @param measurementNoise R scale — higher trusts the prediction over noisy detections.
 */
class KalmanFilter(
    initialX: Double,
    initialY: Double,
    private val processNoise: Double = 1e-2,
    private val measurementNoise: Double = 1e-1,
) {
    // State vector as a 4×1 matrix.
    private var x = arrayOf(
        doubleArrayOf(initialX),
        doubleArrayOf(initialY),
        doubleArrayOf(0.0),
        doubleArrayOf(0.0),
    )

    // State covariance — start uncertain about velocity.
    private var p = arrayOf(
        doubleArrayOf(1.0, 0.0, 0.0, 0.0),
        doubleArrayOf(0.0, 1.0, 0.0, 0.0),
        doubleArrayOf(0.0, 0.0, 1000.0, 0.0),
        doubleArrayOf(0.0, 0.0, 0.0, 1000.0),
    )

    private val f = arrayOf(
        doubleArrayOf(1.0, 0.0, 1.0, 0.0),
        doubleArrayOf(0.0, 1.0, 0.0, 1.0),
        doubleArrayOf(0.0, 0.0, 1.0, 0.0),
        doubleArrayOf(0.0, 0.0, 0.0, 1.0),
    )
    private val h = arrayOf(
        doubleArrayOf(1.0, 0.0, 0.0, 0.0),
        doubleArrayOf(0.0, 1.0, 0.0, 0.0),
    )
    private val q = scaleDiagonal(4, processNoise)
    private val r = scaleDiagonal(2, measurementNoise)

    val posX: Double get() = x[0][0]
    val posY: Double get() = x[1][0]
    val velX: Double get() = x[2][0]
    val velY: Double get() = x[3][0]

    /** Advance the state one frame (x = Fx, P = FPFᵀ + Q). */
    fun predict() {
        x = Matrix.mul(f, x)
        p = Matrix.add(Matrix.mul(Matrix.mul(f, p), Matrix.transpose(f)), q)
    }

    /** Correct with a measured centre [mx, my]. */
    fun update(mx: Double, my: Double) {
        val z = arrayOf(doubleArrayOf(mx), doubleArrayOf(my))
        val ht = Matrix.transpose(h)
        val y = Matrix.sub(z, Matrix.mul(h, x))                 // innovation
        val s = Matrix.add(Matrix.mul(Matrix.mul(h, p), ht), r) // innovation covariance
        val k = Matrix.mul(Matrix.mul(p, ht), Matrix.invert2x2(s)) // Kalman gain
        x = Matrix.add(x, Matrix.mul(k, y))
        val kh = Matrix.mul(k, h)
        p = Matrix.mul(Matrix.sub(Matrix.identity(4), kh), p)
    }

    private fun scaleDiagonal(n: Int, scale: Double): Array<DoubleArray> =
        Array(n) { i -> DoubleArray(n) { j -> if (i == j) scale else 0.0 } }
}

package com.haloxtraffic.feature.violations.tracking

/**
 * Minimal dense matrix ops (row-major `Array<DoubleArray>`) for the Kalman filter — just enough to keep
 * the tracker dependency-free (§4 "no heavy dep"). Small and unit-testable.
 */
internal object Matrix {

    fun zeros(rows: Int, cols: Int): Array<DoubleArray> = Array(rows) { DoubleArray(cols) }

    fun identity(n: Int): Array<DoubleArray> = Array(n) { i -> DoubleArray(n) { j -> if (i == j) 1.0 else 0.0 } }

    fun mul(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
        val rows = a.size
        val inner = b.size
        val cols = b[0].size
        val out = zeros(rows, cols)
        for (i in 0 until rows) for (k in 0 until inner) {
            val aik = a[i][k]
            if (aik == 0.0) continue
            for (j in 0 until cols) out[i][j] += aik * b[k][j]
        }
        return out
    }

    fun transpose(a: Array<DoubleArray>): Array<DoubleArray> {
        val out = zeros(a[0].size, a.size)
        for (i in a.indices) for (j in a[0].indices) out[j][i] = a[i][j]
        return out
    }

    fun add(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> =
        Array(a.size) { i -> DoubleArray(a[0].size) { j -> a[i][j] + b[i][j] } }

    fun sub(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> =
        Array(a.size) { i -> DoubleArray(a[0].size) { j -> a[i][j] - b[i][j] } }

    /** Invert a 2×2 matrix (the Kalman innovation covariance S). */
    fun invert2x2(m: Array<DoubleArray>): Array<DoubleArray> {
        val det = m[0][0] * m[1][1] - m[0][1] * m[1][0]
        require(kotlin.math.abs(det) > 1e-12) { "Singular 2x2 matrix" }
        val inv = 1.0 / det
        return arrayOf(
            doubleArrayOf(m[1][1] * inv, -m[0][1] * inv),
            doubleArrayOf(-m[1][0] * inv, m[0][0] * inv),
        )
    }
}

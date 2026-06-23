package com.haloxtraffic.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeometryOpsTest {

    private val square = Polygon(
        listOf(NormPoint(0.2f, 0.2f), NormPoint(0.8f, 0.2f), NormPoint(0.8f, 0.8f), NormPoint(0.2f, 0.8f)),
    )

    private fun box(l: Float, t: Float, r: Float, b: Float) = BoundingBox(l, t, r, b, 1f, 0)

    @Test fun `point inside and outside polygon`() {
        assertThat(GeometryOps.pointInPolygon(0.5f, 0.5f, square)).isTrue()
        assertThat(GeometryOps.pointInPolygon(0.05f, 0.05f, square)).isFalse()
        assertThat(GeometryOps.pointInPolygon(0.9f, 0.5f, square)).isFalse()
    }

    @Test fun `box center crossing the stop-line band`() {
        assertThat(GeometryOps.boxCenterInPolygon(box(0.4f, 0.4f, 0.6f, 0.6f), square)).isTrue()
        assertThat(GeometryOps.boxCenterInPolygon(box(0.0f, 0.0f, 0.1f, 0.1f), square)).isFalse()
    }

    @Test fun `box straddles a vertical lane boundary`() {
        val divider = LaneBoundary(listOf(NormPoint(0.5f, 0f), NormPoint(0.5f, 1f)))
        assertThat(GeometryOps.boxStraddlesBoundary(box(0.4f, 0.4f, 0.6f, 0.6f), divider)).isTrue()
        assertThat(GeometryOps.boxStraddlesBoundary(box(0.6f, 0.4f, 0.7f, 0.6f), divider)).isFalse()
    }
}

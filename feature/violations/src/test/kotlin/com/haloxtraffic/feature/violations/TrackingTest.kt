package com.haloxtraffic.feature.violations

import com.google.common.truth.Truth.assertThat
import com.haloxtraffic.core.model.BoundingBox
import com.haloxtraffic.feature.violations.tracking.KalmanFilter
import com.haloxtraffic.feature.violations.tracking.MultiObjectTracker
import org.junit.Test

class TrackingTest {

    @Test fun `kalman converges to constant velocity and predicts forward`() {
        val kf = KalmanFilter(0.0, 0.0)
        for (i in 1..15) {
            kf.predict()
            kf.update(i.toDouble(), 0.0) // x advances by 1 each frame
        }
        assertThat(kf.velX).isWithin(0.2).of(1.0)
        assertThat(kf.velY).isWithin(0.2).of(0.0)
        val before = kf.posX
        kf.predict()
        assertThat(kf.posX).isGreaterThan(before)
    }

    @Test fun `tracker keeps one stable id for a moving box and estimates rightward velocity`() {
        val tracker = MultiObjectTracker()
        var lastTracks = emptyList<com.haloxtraffic.core.model.Track>()
        for (f in 0..7) {
            val x = 0.10f + 0.02f * f
            val det = BoundingBox(x, 0.4f, x + 0.2f, 0.6f, score = 0.9f, classId = 1) // car
            lastTracks = tracker.update(listOf(det), f.toLong())
        }
        assertThat(lastTracks).hasSize(1)
        assertThat(lastTracks.first().trackId).isEqualTo(1L)
        assertThat(lastTracks.first().velocity.x).isGreaterThan(0f)
    }

    @Test fun `tracker retires a track after it disappears`() {
        val tracker = MultiObjectTracker(maxAge = 2)
        repeat(4) { f ->
            tracker.update(listOf(BoundingBox(0.4f, 0.4f, 0.6f, 0.6f, 0.9f, 1)), f.toLong())
        }
        // Now feed empty frames beyond maxAge → the track is dropped.
        var tracks = listOf<com.haloxtraffic.core.model.Track>()
        repeat(4) { f -> tracks = tracker.update(emptyList(), (10 + f).toLong()) }
        assertThat(tracks).isEmpty()
    }
}

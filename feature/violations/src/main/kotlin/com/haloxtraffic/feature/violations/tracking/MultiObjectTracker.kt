package com.haloxtraffic.feature.violations.tracking

import com.haloxtraffic.core.model.BoundingBox
import com.haloxtraffic.core.model.DetectionClass
import com.haloxtraffic.core.model.NormPoint
import com.haloxtraffic.core.model.Track
import com.haloxtraffic.core.model.VehicleClass

/**
 * Lightweight IoU + Kalman multi-object tracker (§4 Stage 2). Each frame: predict every track, greedily
 * associate detections by IoU, update matches, spawn tracks for new detections, and retire stale ones.
 * Operates in normalised [0,1] coordinates so it is resolution-independent. Velocity (normalised
 * units/frame) feeds direction-of-travel estimation for the wrong-way FSM.
 *
 * @param iouThreshold minimum IoU to associate a detection with a track.
 * @param maxAge frames a track survives without a detection before being dropped.
 * @param minHits detections before a track is considered "confirmed" (reported to FSMs).
 */
class MultiObjectTracker(
    private val iouThreshold: Float = 0.3f,
    private val maxAge: Int = 15,
    private val minHits: Int = 3,
) {
    private val trackers = mutableListOf<KalmanBoxTracker>()
    private var nextId = 1L

    /**
     * @param detections vehicle detections for this frame (boxes carry classId).
     * @param frame monotonically increasing frame index.
     * @return confirmed, currently-visible tracks.
     */
    fun update(detections: List<BoundingBox>, frame: Long): List<Track> {
        trackers.forEach { it.predict() }

        val matched = associate(detections)
        val usedDetections = HashSet<Int>()
        matched.forEach { (trackIdx, detIdx) ->
            trackers[trackIdx].update(detections[detIdx], frame)
            usedDetections += detIdx
        }

        detections.indices.filterNot { it in usedDetections }.forEach { detIdx ->
            val det = detections[detIdx]
            trackers += KalmanBoxTracker(nextId++, det, frame, classOf(det))
        }

        trackers.removeAll { it.timeSinceUpdate > maxAge }

        return trackers
            .filter { it.timeSinceUpdate == 0 && (it.hits >= minHits || it.firstFrame == frame) }
            .map { it.toTrack() }
    }

    /** Greedy IoU association: highest-IoU pairs first, each track/detection used once. */
    private fun associate(detections: List<BoundingBox>): List<Pair<Int, Int>> {
        if (trackers.isEmpty() || detections.isEmpty()) return emptyList()
        data class Cand(val t: Int, val d: Int, val iou: Float)
        val cands = ArrayList<Cand>()
        trackers.forEachIndexed { ti, tr ->
            val tb = tr.predictedBox()
            detections.forEachIndexed { di, det ->
                val iou = tb.iou(det)
                if (iou >= iouThreshold) cands += Cand(ti, di, iou)
            }
        }
        cands.sortByDescending { it.iou }
        val usedT = HashSet<Int>()
        val usedD = HashSet<Int>()
        val out = ArrayList<Pair<Int, Int>>()
        for (c in cands) {
            if (c.t in usedT || c.d in usedD) continue
            usedT += c.t; usedD += c.d
            out += c.t to c.d
        }
        return out
    }

    fun reset() {
        trackers.clear()
        nextId = 1L
    }

    private fun classOf(box: BoundingBox): VehicleClass =
        DetectionClass.fromId(box.classId)?.vehicleClass ?: VehicleClass.UNKNOWN
}

/** One tracked object: a Kalman-smoothed centre + EMA size + lifecycle counters. */
internal class KalmanBoxTracker(
    val id: Long,
    initial: BoundingBox,
    val firstFrame: Long,
    private var vehicleClass: VehicleClass,
) {
    private val kf = KalmanFilter(initial.centerX.toDouble(), initial.centerY.toDouble())
    private var w = initial.width
    private var h = initial.height
    var hits = 1
        private set
    var timeSinceUpdate = 0
        private set
    private var lastFrame = firstFrame

    fun predict() {
        kf.predict()
        timeSinceUpdate++
    }

    fun update(box: BoundingBox, frame: Long) {
        kf.update(box.centerX.toDouble(), box.centerY.toDouble())
        w = EMA * w + (1 - EMA) * box.width
        h = EMA * h + (1 - EMA) * box.height
        DetectionClass.fromId(box.classId)?.vehicleClass?.let { vehicleClass = it }
        hits++
        timeSinceUpdate = 0
        lastFrame = frame
    }

    fun predictedBox(): BoundingBox = boxAt(kf.posX.toFloat(), kf.posY.toFloat())

    fun toTrack(): Track = Track(
        trackId = id,
        vehicleClass = vehicleClass,
        box = predictedBox(),
        velocity = NormPoint(kf.velX.toFloat(), kf.velY.toFloat()),
        firstSeenFrame = firstFrame,
        lastSeenFrame = lastFrame,
    )

    private fun boxAt(cx: Float, cy: Float): BoundingBox = BoundingBox(
        left = (cx - w / 2f).coerceIn(0f, 1f),
        top = (cy - h / 2f).coerceIn(0f, 1f),
        right = (cx + w / 2f).coerceIn(0f, 1f),
        bottom = (cy + h / 2f).coerceIn(0f, 1f),
        score = 1f,
        classId = -1,
    )

    private companion object {
        const val EMA = 0.6f
    }
}

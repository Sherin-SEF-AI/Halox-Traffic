package com.haloxtraffic.core.data.analytics

import com.haloxtraffic.core.data.entity.ViolationCaseEntity
import com.haloxtraffic.core.model.CaseStatus
import com.haloxtraffic.core.model.ViolationType

/**
 * Review-driven precision: how often a human confirmed a flagged case versus dismissed it as a false
 * alarm. This is the real correctness signal — a low precision means the detector is over-firing.
 */
data class ReviewMetrics(
    val confirmed: Int,
    val dismissed: Int,
) {
    val reviewed: Int get() = confirmed + dismissed

    /** confirmed / reviewed, or null until at least one case has been reviewed. */
    val precision: Float? get() = if (reviewed == 0) null else confirmed.toFloat() / reviewed

    companion object {
        fun of(cases: List<ViolationCaseEntity>) = ReviewMetrics(
            confirmed = cases.count { it.status == CaseStatus.CONFIRMED },
            dismissed = cases.count { it.status == CaseStatus.DISMISSED },
        )
    }
}

/** Session/lifetime analytics derived from cases (§12.6). */
data class CaseAnalytics(
    val total: Int,
    val byType: Map<ViolationType, Int>,
    /** Fraction of cases whose plate was validated. */
    val validatedRate: Float,
    /** Fraction whose plate is missing or unvalidated (needs review). */
    val uncertainRate: Float,
    /** Plates seen more than once, most-frequent first. */
    val repeatPlates: List<Pair<String, Int>>,
    /** Violation counts by hour-of-day (UTC), 0..23. */
    val byHour: Map<Int, Int>,
    /** Precision over all reviewed cases (confirmed vs dismissed by a human). */
    val review: ReviewMetrics,
    /** Precision broken down per violation type, so an over-firing type is visible. */
    val reviewByType: Map<ViolationType, ReviewMetrics>,
    /** Cases still awaiting human review (status OPEN or REVIEWED). */
    val pendingReview: Int,
) {
    companion object {
        val EMPTY = CaseAnalytics(0, emptyMap(), 0f, 0f, emptyList(), emptyMap(), ReviewMetrics(0, 0), emptyMap(), 0)
    }
}

/** Pure analytics over a case list — unit-testable, no Android deps. */
object Analytics {
    fun compute(cases: List<ViolationCaseEntity>): CaseAnalytics {
        if (cases.isEmpty()) return CaseAnalytics.EMPTY
        val total = cases.size

        val byType = cases.groupingBy { it.type }.eachCount()
        val validated = cases.count { it.plateValidated }
        val uncertain = cases.count { it.plateString == null || !it.plateValidated }

        val repeatPlates = cases.mapNotNull { it.plateString }
            .groupingBy { it }.eachCount()
            .filter { it.value > 1 }
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }

        val byHour = cases.groupingBy { ((it.ts / 3_600_000L) % 24).toInt() }.eachCount()

        val review = ReviewMetrics.of(cases)
        val reviewByType = cases.groupBy { it.type }.mapValues { ReviewMetrics.of(it.value) }
        val pendingReview = cases.count { it.status == CaseStatus.OPEN || it.status == CaseStatus.REVIEWED }

        return CaseAnalytics(
            total = total,
            byType = byType,
            validatedRate = validated.toFloat() / total,
            uncertainRate = uncertain.toFloat() / total,
            repeatPlates = repeatPlates,
            byHour = byHour,
            review = review,
            reviewByType = reviewByType,
            pendingReview = pendingReview,
        )
    }
}

package com.haloxtraffic.core.data.analytics

import com.haloxtraffic.core.data.entity.ViolationCaseEntity
import com.haloxtraffic.core.model.ViolationType

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
) {
    companion object {
        val EMPTY = CaseAnalytics(0, emptyMap(), 0f, 0f, emptyList(), emptyMap())
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

        return CaseAnalytics(
            total = total,
            byType = byType,
            validatedRate = validated.toFloat() / total,
            uncertainRate = uncertain.toFloat() / total,
            repeatPlates = repeatPlates,
            byHour = byHour,
        )
    }
}

package com.haloxtraffic.feature.anpr

import com.haloxtraffic.core.model.PlateFormat
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of validating a canonical plate string. */
data class PlateValidation(
    val canonical: String,
    val format: PlateFormat,
    /** True only when format regex AND (for state-series) a valid state code both pass. */
    val valid: Boolean,
)

/**
 * Classifies + validates a canonical Indian plate (§7D). A plate that detects but fails regex/state
 * checks is [PlateFormat.NON_CONFORMANT] with valid=false — itself enforceable (HSRP non-compliance).
 */
@Singleton
class PlateValidator @Inject constructor() {

    fun validate(rawOrCanonical: String): PlateValidation {
        val c = IndianPlate.canonicalize(rawOrCanonical)
        return when {
            IndianPlate.BH_SERIES.matches(c) ->
                PlateValidation(c, PlateFormat.BH_SERIES, valid = true)

            IndianPlate.STANDARD.matches(c) ->
                PlateValidation(c, PlateFormat.STANDARD, valid = IndianPlate.hasValidStateCode(c))

            IndianPlate.TEMPORARY.matches(c) ->
                PlateValidation(c, PlateFormat.TEMPORARY, valid = true)

            else -> PlateValidation(c, PlateFormat.NON_CONFORMANT, valid = false)
        }
    }
}

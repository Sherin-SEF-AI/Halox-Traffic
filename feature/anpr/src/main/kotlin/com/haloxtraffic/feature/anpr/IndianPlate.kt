package com.haloxtraffic.feature.anpr

/**
 * Indian plate reference data (§7). The state/UT code whitelist rejects impossible plates; the format
 * regexes classify a candidate after canonicalisation (uppercase, no spaces/hyphens).
 */
object IndianPlate {

    /** Official state + UT registration codes (the leading two letters). */
    val STATE_CODES: Set<String> = setOf(
        // States
        "AP", "AR", "AS", "BR", "CG", "GA", "GJ", "HR", "HP", "JH", "KA", "KL", "MP", "MH",
        "MN", "ML", "MZ", "NL", "OD", "PB", "RJ", "SK", "TN", "TS", "TR", "UP", "UK", "WB",
        // Union territories
        "AN", "CH", "DD", "DL", "DN", "JK", "LA", "LD", "PY",
        // Legacy / transitional still seen in the field
        "OR", "UA", "CT",
    )

    /** Standard state-series HSRP: XX NN X(X)(X) NNNN → canonical AA00AA0000 (1–3 series letters, 1–2 RTO digits). */
    val STANDARD = Regex("^[A-Z]{2}[0-9]{1,2}[A-Z]{1,3}[0-9]{4}$")

    /** Bharat (BH) series: YY BH NNNN X(X), suffix letters exclude I and O. */
    val BH_SERIES = Regex("^[0-9]{2}BH[0-9]{4}[A-HJ-NP-Z]{1,2}$")

    /** Temporary registration: leading T + state + numerics. */
    val TEMPORARY = Regex("^T[A-Z]{1,2}[0-9]{2,}.*$")

    /** Strip spaces, hyphens and lowercase noise to the canonical comparison form. */
    fun canonicalize(raw: String): String =
        raw.uppercase().filter { it.isLetterOrDigit() }

    fun hasValidStateCode(canonical: String): Boolean =
        canonical.length >= 2 && canonical.take(2) in STATE_CODES
}

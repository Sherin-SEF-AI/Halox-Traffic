package com.haloxtraffic.core.export

import java.io.File

/** Export formats (§8/§12.6). */
enum class ExportFormat {
    /** e-challan-compatible bundle: media + metadata JSON + hash/signature + chain pointer. */
    ECHALLAN_BUNDLE,

    /** Human-review case-file PDF. */
    PDF_CASE_FILE,

    /** CSV index of cases. */
    CSV_INDEX,
}

/**
 * Produces enforcement-ready exports from sealed cases (§8). Bundles include the package hash,
 * signature and chain pointer so the receiving system can re-verify integrity independently. Contract
 * defined now; implementations land in Phase 8.
 */
interface EvidenceExporter {
    /**
     * Export one case (PDF / e-challan bundle). When [blurFaces] is true, bystander faces in the
     * exported stills are mosaicked (a derivative copy — the sealed original is untouched).
     */
    suspend fun exportCase(caseId: String, format: ExportFormat, outDir: File, blurFaces: Boolean = false): Result<File>

    /** Export a CSV index across many cases. */
    suspend fun exportIndex(caseIds: List<String>, outDir: File): Result<File>
}

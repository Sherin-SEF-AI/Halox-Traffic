package com.haloxtraffic.core.evidence

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Immutable, app-private store for sealed evidence media (§8). Files are written once into a per-case
 * directory; there is intentionally **no delete/overwrite API** — sealed evidence is never mutated from
 * the app. Retention purges (Phase 10) happen through a separate, audited path, not here.
 */
@Singleton
class SealedStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val root: File by lazy { File(context.filesDir, "evidence").apply { mkdirs() } }

    fun caseDir(caseId: String): File = File(root, caseId).apply { mkdirs() }

    fun newStillFile(caseId: String, index: Int = 0): File =
        File(caseDir(caseId), "still_${index}_${System.currentTimeMillis()}.jpg")

    fun newCropFile(caseId: String, index: Int = 0): File =
        File(caseDir(caseId), "plate_${index}_${System.currentTimeMillis()}.jpg")

    /** Compress a bitmap to JPEG in the case directory. Returns the written file, or null on failure. */
    fun saveJpeg(file: File, bitmap: Bitmap, quality: Int = 90): File? = runCatching {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        file
    }.getOrElse {
        Timber.e(it, "Failed to write evidence ${file.name}")
        null
    }

    fun saveStill(caseId: String, bitmap: Bitmap, index: Int = 0): File? =
        saveJpeg(newStillFile(caseId, index), bitmap)

    fun saveCrop(caseId: String, bitmap: Bitmap, index: Int = 0): File? =
        saveJpeg(newCropFile(caseId, index), bitmap)

    /** Total bytes used by sealed evidence — for the storage-usage indicator (§12.7). */
    fun totalBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /**
     * Retention-path deletion (§14) — the ONLY way sealed media is removed, and only after a case has
     * synced + its integrity confirmed. Not reachable from the UI.
     */
    fun purgeCase(caseId: String): Boolean = runCatching { caseDir(caseId).deleteRecursively() }.getOrDefault(false)
}

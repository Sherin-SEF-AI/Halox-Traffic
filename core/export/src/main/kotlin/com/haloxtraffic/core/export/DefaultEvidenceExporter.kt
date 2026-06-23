package com.haloxtraffic.core.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.haloxtraffic.core.data.entity.EvidencePackageEntity
import com.haloxtraffic.core.data.entity.ViolationCaseEntity
import com.haloxtraffic.core.data.repository.CaseRepository
import com.haloxtraffic.core.evidence.Signer
import com.haloxtraffic.core.model.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Produces enforcement-ready exports from sealed cases (§8/§12.6): a human-readable PDF case file, an
 * e-challan bundle (media + metadata JSON + hash/signature/chain pointer + public key, zipped) and a
 * CSV index. Bundles let the receiving system re-verify integrity independently.
 */
@Singleton
class DefaultEvidenceExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val caseRepository: CaseRepository,
    private val signer: Signer,
    private val faceBlurrer: FaceBlurrer,
    @IoDispatcher private val io: CoroutineDispatcher,
) : EvidenceExporter {

    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)

    override suspend fun exportCase(caseId: String, format: ExportFormat, outDir: File, blurFaces: Boolean): Result<File> =
        withContext(io) {
            runCatching {
                val case = caseRepository.caseById(caseId) ?: error("Case not found: $caseId")
                val pkg = caseRepository.evidenceFor(caseId)
                outDir.mkdirs()
                when (format) {
                    ExportFormat.PDF_CASE_FILE -> writePdf(case, pkg, File(outDir, "case_${caseId}.pdf"), blurFaces)
                    ExportFormat.ECHALLAN_BUNDLE -> writeBundle(case, pkg, File(outDir, "echallan_${caseId}.zip"), blurFaces)
                    ExportFormat.CSV_INDEX -> writeCsv(listOf(case), File(outDir, "case_${caseId}.csv"))
                }
            }.onFailure { Timber.e(it, "exportCase failed") }
        }

    /** Decode a still and optionally mosaic bystander faces for export. */
    private suspend fun stillBitmap(path: String, blurFaces: Boolean): Bitmap? {
        val bmp = runCatching { BitmapFactory.decodeFile(path) }.getOrNull() ?: return null
        return if (blurFaces) faceBlurrer.blurFaces(bmp) else bmp
    }

    override suspend fun exportIndex(caseIds: List<String>, outDir: File): Result<File> = withContext(io) {
        runCatching {
            val cases = caseIds.mapNotNull { caseRepository.caseById(it) }
            outDir.mkdirs()
            writeCsv(cases, File(outDir, "cases_index_${System.currentTimeMillis()}.csv"))
        }.onFailure { Timber.e(it, "exportIndex failed") }
    }

    private fun metadataOf(case: ViolationCaseEntity, pkg: EvidencePackageEntity?): ExportMetadata {
        val media = (paths(pkg?.stillPathsJson) + paths(pkg?.plateCropPathsJson) + listOfNotNull(pkg?.clipPath))
        return ExportMetadata(
            caseId = case.id, type = case.type.name, tsMs = case.ts, lat = case.lat, lon = case.lon,
            accuracyM = case.accuracyM, plate = case.plateString, plateValidated = case.plateValidated,
            plateColor = case.plateColor?.name, status = case.status.name, vlmDescription = case.vlmDescription,
            sha256 = pkg?.sha256, prevHash = pkg?.prevHash, signature = pkg?.signature,
            publicKeyB64 = signer.publicKeyB64(), timeTrust = pkg?.timeTrustFlag?.name,
            mediaFiles = media.map { File(it).name },
        )
    }

    private suspend fun writeBundle(case: ViolationCaseEntity, pkg: EvidencePackageEntity?, out: File, blurFaces: Boolean): File {
        val stills = paths(pkg?.stillPathsJson).toSet()
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("metadata.json"))
            zip.write(json.encodeToString(metadataOf(case, pkg)).toByteArray())
            zip.closeEntry()
            (paths(pkg?.stillPathsJson) + paths(pkg?.plateCropPathsJson) + listOfNotNull(pkg?.clipPath))
                .map(::File).filter { it.exists() }
                .forEach { f ->
                    zip.putNextEntry(ZipEntry("media/${f.name}"))
                    if (blurFaces && f.absolutePath in stills) {
                        // Stills may show bystanders → export a face-mosaicked derivative.
                        val blurred = stillBitmap(f.absolutePath, blurFaces = true)
                        if (blurred != null) {
                            blurred.compress(Bitmap.CompressFormat.JPEG, 90, zip); blurred.recycle()
                        } else {
                            f.inputStream().use { it.copyTo(zip) }
                        }
                    } else {
                        f.inputStream().use { it.copyTo(zip) }
                    }
                    zip.closeEntry()
                }
        }
        return out
    }

    private suspend fun writePdf(case: ViolationCaseEntity, pkg: EvidencePackageEntity?, out: File, blurFaces: Boolean): File {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create()) // A4 @72dpi
        val canvas = page.canvas
        val title = Paint().apply { color = Color.BLACK; textSize = 20f; isFakeBoldText = true }
        val text = Paint().apply { color = Color.DKGRAY; textSize = 11f }
        var y = 48f
        canvas.drawText("HaloxTraffic — Violation Case", 36f, y, title); y += 28f
        val lines = listOf(
            "Case: ${case.id}",
            "Type: ${case.type.displayName}",
            "When: ${timeFmt.format(Date(case.ts))}  (${pkg?.timeTrustFlag?.name ?: "?"})",
            "Plate: ${case.plateString ?: "uncertain"}  validated=${case.plateValidated}",
            "Location: ${"%.6f, %.6f".format(case.lat, case.lon)}  ±${case.accuracyM}m",
            "Status: ${case.status.name}",
            "Seal SHA-256: ${pkg?.sha256 ?: "—"}",
            "Signature: ${pkg?.signature?.take(40) ?: "—"}…",
            case.vlmDescription?.let { "Note: $it" } ?: "",
        )
        lines.filter { it.isNotEmpty() }.forEach { canvas.drawText(it, 36f, y, text); y += 18f }

        // Embed the first still if present (face-mosaicked when requested).
        paths(pkg?.stillPathsJson).firstOrNull()?.let { p ->
            stillBitmap(p, blurFaces)?.let { bmp ->
                val scale = (523f / bmp.width).coerceAtMost(380f / bmp.height)
                val w = bmp.width * scale
                val h = bmp.height * scale
                canvas.drawBitmap(bmp, null, android.graphics.RectF(36f, y + 8f, 36f + w, y + 8f + h), null)
                bmp.recycle()
            }
        }
        doc.finishPage(page)
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private fun writeCsv(cases: List<ViolationCaseEntity>, out: File): File {
        out.bufferedWriter().use { w ->
            w.appendLine("caseId,type,timestamp,plate,validated,lat,lon,accuracyM,status")
            cases.forEach { c ->
                w.appendLine(
                    listOf(
                        c.id, c.type.name, timeFmt.format(Date(c.ts)), c.plateString ?: "",
                        c.plateValidated, c.lat, c.lon, c.accuracyM, c.status.name,
                    ).joinToString(",") { csvCell(it.toString()) },
                )
            }
        }
        return out
    }

    private fun csvCell(s: String): String =
        if (s.contains(',') || s.contains('"')) "\"${s.replace("\"", "\"\"")}\"" else s

    private fun paths(jsonArray: String?): List<String> =
        jsonArray?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList()) } ?: emptyList()
}

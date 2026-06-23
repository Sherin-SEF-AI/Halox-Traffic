package com.haloxtraffic.feature.casefile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.haloxtraffic.core.data.entity.EvidencePackageEntity
import com.haloxtraffic.core.data.entity.ViolationCaseEntity
import com.haloxtraffic.core.designsystem.component.ButtonKind
import com.haloxtraffic.core.designsystem.component.ConfidenceTag
import com.haloxtraffic.core.designsystem.component.HairlineDivider
import com.haloxtraffic.core.designsystem.component.HaloxCard
import com.haloxtraffic.core.designsystem.component.OperationalButton
import com.haloxtraffic.core.designsystem.component.PlateReadout
import com.haloxtraffic.core.designsystem.component.StatusPill
import com.haloxtraffic.core.designsystem.component.TelemetryRow
import com.haloxtraffic.core.designsystem.theme.HaloxTheme
import com.haloxtraffic.core.designsystem.theme.SignalLevel
import com.haloxtraffic.core.model.TimeTrust
import com.haloxtraffic.core.model.ViolationType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Case detail (§12.4): sealed evidence, integrity, plate-correction (append-only) + confirm/dismiss. */
@Composable
fun CaseDetailScreen(
    caseId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CaseFileViewModel = hiltViewModel(),
) {
    val case by viewModel.case(caseId).collectAsStateWithLifecycle(initialValue = null)
    val audit by viewModel.audit(caseId).collectAsStateWithLifecycle(initialValue = emptyList())
    val evidence by produceState<EvidencePackageEntity?>(null, caseId) { value = viewModel.evidence(caseId) }
    val verified by produceState<Boolean?>(null, caseId, evidence) {
        value = if (evidence == null) null else viewModel.verify(caseId)
    }

    val c = case ?: run {
        Text("Loading…", modifier.padding(24.dp), color = HaloxTheme.colors.inkMuted)
        return
    }

    Column(
        modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            ViolationType.entries.firstOrNull { it.name == c.type.name }?.displayName ?: c.type.name,
            style = HaloxTheme.typography.title,
            color = HaloxTheme.colors.ink,
        )

        // Plate + validation + integrity.
        HaloxCard {
            if (c.plateString != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlateReadout(c.plateString!!)
                    ConfidenceTag(c.plateConfidence ?: 0f, c.plateValidated)
                }
            } else {
                StatusPill("PLATE UNCERTAIN", SignalLevel.DEGRADED)
            }
            HairlineDivider(Modifier.padding(vertical = 10.dp))
            TelemetryRow("WHEN", formatTime(c.ts))
            TelemetryRow(
                "TIME",
                if (evidence?.timeTrustFlag == TimeTrust.TRUSTED) "trusted" else "untrusted",
            )
            TelemetryRow("GEO", "%.5f, %.5f  ±%.0fm".format(c.lat, c.lon, c.accuracyM))
            TelemetryRow("STATUS", c.status.name)
            IntegrityRow(verified, evidence)
        }

        // Evidence media.
        val stills = parsePaths(evidence?.stillPathsJson)
        val crops = parsePaths(evidence?.plateCropPathsJson)
        if (stills.isNotEmpty() || crops.isNotEmpty()) {
            HaloxCard {
                Text("EVIDENCE", style = HaloxTheme.typography.labelMicro, color = HaloxTheme.colors.inkFaint)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    crops.forEach { FileImage(it, Modifier.width(120.dp).aspectRatio(2.4f)) }
                }
                stills.forEach {
                    FileImage(it, Modifier.fillMaxWidth().aspectRatio(1.4f).padding(top = 8.dp))
                }
            }
        }

        // FSM decision trace.
        HaloxCard {
            Text("DECISION TRACE", style = HaloxTheme.typography.labelMicro, color = HaloxTheme.colors.inkFaint)
            Text(
                c.fsmTraceJson.take(600),
                style = HaloxTheme.typography.dataSmall,
                color = HaloxTheme.colors.inkMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        // Append-only plate audit trail.
        if (audit.isNotEmpty()) {
            HaloxCard {
                Text("PLATE AUDIT", style = HaloxTheme.typography.labelMicro, color = HaloxTheme.colors.inkFaint)
                audit.forEach { a ->
                    val line = if (a.correctedRead != null) {
                        "${a.ts.let(::formatTime)}  ${a.originalRead ?: "—"} → ${a.correctedRead}  (${a.reviewerId ?: "?"})"
                    } else {
                        "${a.ts.let(::formatTime)}  ${a.originalRead ?: "uncertain"}  (${a.reason})"
                    }
                    TelemetryRow("·", line)
                }
            }
        }

        PlateCorrection(onApply = { plate, reason -> viewModel.correctPlate(caseId, plate, reason) })

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OperationalButton("Confirm", { viewModel.confirm(caseId) }, kind = ButtonKind.PRIMARY)
            OperationalButton("Dismiss", { viewModel.dismiss(caseId) })
            OperationalButton("Back", onBack)
        }
    }
}

@Composable
private fun IntegrityRow(verified: Boolean?, evidence: EvidencePackageEntity?) {
    val (label, level) = when {
        evidence == null -> "no package" to SignalLevel.DEGRADED
        verified == null -> "checking…" to SignalLevel.NEUTRAL
        verified -> "verified" to SignalLevel.CONFIRMED
        else -> "TAMPERED" to SignalLevel.VIOLATION
    }
    TelemetryRow("SEAL", evidence?.sha256?.take(16)?.plus("…") ?: "—", trailing = { StatusPill(label, level) })
}

@Composable
private fun PlateCorrection(onApply: (String, String) -> Unit) {
    var plate by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    HaloxCard {
        Text("CORRECT PLATE (AUDITED)", style = HaloxTheme.typography.labelMicro, color = HaloxTheme.colors.inkFaint)
        OutlinedTextField(
            value = plate, onValueChange = { plate = it },
            label = { Text("Corrected plate") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = reason, onValueChange = { reason = it },
            label = { Text("Reason") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OperationalButton(
            "Apply correction",
            { if (plate.isNotBlank()) onApply(plate, reason.ifBlank { "manual correction" }) },
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
private fun formatTime(ms: Long): String = timeFormat.format(Date(ms))

private fun parsePaths(json: String?): List<String> =
    json?.let { runCatching { Json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList()) } ?: emptyList()

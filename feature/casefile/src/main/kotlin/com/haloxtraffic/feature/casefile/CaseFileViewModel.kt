package com.haloxtraffic.feature.casefile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haloxtraffic.core.data.entity.EvidencePackageEntity
import com.haloxtraffic.core.data.entity.PlateAuditEntity
import com.haloxtraffic.core.data.entity.ViolationCaseEntity
import com.haloxtraffic.core.data.repository.CaseRepository
import com.haloxtraffic.core.data.repository.SealingRepository
import com.haloxtraffic.core.data.settings.SettingsRepository
import com.haloxtraffic.core.export.EvidenceExporter
import com.haloxtraffic.core.export.ExportFormat
import com.haloxtraffic.core.model.CaseStatus
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CaseFileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val caseRepository: CaseRepository,
    private val sealingRepository: SealingRepository,
    private val settingsRepository: SettingsRepository,
    private val exporter: EvidenceExporter,
) : ViewModel() {

    val cases: StateFlow<List<ViolationCaseEntity>> =
        caseRepository.observeCases()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun case(id: String): Flow<ViolationCaseEntity?> = caseRepository.observeCase(id)
    fun audit(id: String): Flow<List<PlateAuditEntity>> = caseRepository.observeAudit(id)

    suspend fun evidence(id: String): EvidencePackageEntity? = caseRepository.evidenceFor(id)
    suspend fun verify(id: String): Boolean = sealingRepository.verifyCase(id)

    fun confirm(id: String) = viewModelScope.launch { sealingRepository.setStatus(id, CaseStatus.CONFIRMED) }
    fun dismiss(id: String) = viewModelScope.launch { sealingRepository.setStatus(id, CaseStatus.DISMISSED) }

    fun correctPlate(id: String, corrected: String, reason: String) = viewModelScope.launch {
        val officer = settingsRepository.settings.first().officerId.ifBlank { "reviewer" }
        sealingRepository.correctPlate(id, corrected.uppercase().filter { it.isLetterOrDigit() }, officer, reason)
    }

    /** Export a case (PDF / e-challan bundle) to cache; [onReady] receives the file to share. */
    fun export(id: String, format: ExportFormat, onReady: (File) -> Unit) = viewModelScope.launch {
        val blur = settingsRepository.settings.first().bystanderBlurDefault
        exporter.exportCase(id, format, File(context.cacheDir, "exports"), blurFaces = blur).onSuccess(onReady)
    }
}

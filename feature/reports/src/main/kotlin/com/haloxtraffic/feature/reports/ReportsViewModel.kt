package com.haloxtraffic.feature.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haloxtraffic.core.data.analytics.Analytics
import com.haloxtraffic.core.data.analytics.CaseAnalytics
import com.haloxtraffic.core.data.repository.CaseRepository
import com.haloxtraffic.core.export.EvidenceExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val caseRepository: CaseRepository,
    private val exporter: EvidenceExporter,
) : ViewModel() {

    val analytics: StateFlow<CaseAnalytics> =
        caseRepository.observeCases().map { Analytics.compute(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaseAnalytics.EMPTY)

    private val exportsDir: File get() = File(context.cacheDir, "exports")

    /** Export a CSV index of all cases; calls [onReady] with the file on success. */
    fun exportCsvIndex(onReady: (File) -> Unit) = viewModelScope.launch {
        val ids = caseRepository.observeCases().first().map { it.id }
        exporter.exportIndex(ids, exportsDir).onSuccess(onReady)
    }
}

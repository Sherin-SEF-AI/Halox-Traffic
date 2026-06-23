package com.haloxtraffic.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haloxtraffic.core.data.repository.CaseRepository
import com.haloxtraffic.core.model.ViolationType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** A plotted violation point. */
data class MapPoint(
    val caseId: String,
    val lat: Double,
    val lon: Double,
    val type: ViolationType,
    val validated: Boolean,
)

data class MapFilters(val type: ViolationType? = null, val onlyUncertain: Boolean = false)

@HiltViewModel
class MapViewModel @Inject constructor(
    caseRepository: CaseRepository,
) : ViewModel() {

    private val _filters = MutableStateFlow(MapFilters())
    val filters: StateFlow<MapFilters> = _filters

    val points: StateFlow<List<MapPoint>> =
        combine(caseRepository.observeCases(), _filters) { cases, f ->
            cases.asSequence()
                .filter { it.lat != 0.0 || it.lon != 0.0 } // skip cases with no fix
                .filter { f.type == null || it.type == f.type }
                .filter { !f.onlyUncertain || !it.plateValidated }
                .map { MapPoint(it.id, it.lat, it.lon, it.type, it.plateValidated) }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setType(type: ViolationType?) { _filters.value = _filters.value.copy(type = type) }
    fun toggleUncertain() { _filters.value = _filters.value.copy(onlyUncertain = !_filters.value.onlyUncertain) }
}

package com.haloxtraffic.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haloxtraffic.core.data.settings.AppSettings
import com.haloxtraffic.core.data.settings.SettingsRepository
import com.haloxtraffic.core.model.DeviceTier
import com.haloxtraffic.core.model.ViolationType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setTierOverride(tier: DeviceTier?) = viewModelScope.launch { repository.setTierOverride(tier) }
    fun setBystanderBlur(enabled: Boolean) = viewModelScope.launch { repository.setBystanderBlurDefault(enabled) }
    fun setOfficer(id: String) = viewModelScope.launch { repository.setOfficer(id) }
    fun setRetentionDays(days: Int) = viewModelScope.launch { repository.setRetentionDays(days) }
    fun setViolationEnabled(type: ViolationType, enabled: Boolean) =
        viewModelScope.launch { repository.setViolationEnabled(type, enabled) }
}

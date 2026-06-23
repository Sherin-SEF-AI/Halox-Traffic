package com.haloxtraffic.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haloxtraffic.core.data.settings.SettingsRepository
import com.haloxtraffic.core.model.DetectionConfig
import com.haloxtraffic.core.model.DeviceProfile
import com.haloxtraffic.core.model.DeviceTier
import com.haloxtraffic.core.sensors.profile.DeviceProfiler
import com.haloxtraffic.feature.detection.model.ModelRegistry
import com.haloxtraffic.feature.detection.model.ModelSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val officerId: String = "",
    val profile: DeviceProfile? = null,
    val effectiveTier: DeviceTier = DeviceTier.LOW,
    val tierReasoning: List<String> = emptyList(),
    val requiredModels: List<ModelSpec> = emptyList(),
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val deviceProfiler: DeviceProfiler,
    private val settingsRepository: SettingsRepository,
    private val modelRegistry: ModelRegistry,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init { profile() }

    private fun profile() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val p = deviceProfiler.profile()
            val tier = settings.tierOverride ?: p.tier
            val config = DetectionConfig.forTier(tier)
            _state.value = _state.value.copy(
                officerId = settings.officerId,
                profile = p,
                effectiveTier = tier,
                tierReasoning = listOf(
                    "ram=${p.totalRamMb}MB",
                    "soc=${p.socModel}",
                    "abi=${p.abis.firstOrNull() ?: "?"}",
                    "gpu=${if (p.gpuDelegateAvailable) "yes" else "no"} nnapi=${if (p.nnapiAvailable) "yes" else "no"}",
                    "vlm=${if (config.vlmEnabled) "enabled" else "disabled"}",
                ),
                requiredModels = modelRegistry.specsFor(config),
            )
        }
    }

    fun setOfficerId(id: String) {
        _state.value = _state.value.copy(officerId = id)
    }

    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.setOfficer(_state.value.officerId.ifBlank { "unassigned" })
            onDone()
        }
    }
}

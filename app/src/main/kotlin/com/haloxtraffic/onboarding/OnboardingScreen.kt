package com.haloxtraffic.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.haloxtraffic.core.designsystem.component.ButtonKind
import com.haloxtraffic.core.designsystem.component.HaloxCard
import com.haloxtraffic.core.designsystem.component.MonoValue
import com.haloxtraffic.core.designsystem.component.OperationalButton
import com.haloxtraffic.core.designsystem.component.TierResultCard
import com.haloxtraffic.core.designsystem.theme.HaloxTheme

/**
 * Onboarding (§12.1): officer identity → device profiling + tier reveal → model provisioning preview.
 * The tier is shown as a confident result card with the *why*; permissions are requested on the Live
 * screen at the point of use (clearer rationale than up-front).
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Set up", style = HaloxTheme.typography.title, color = HaloxTheme.colors.ink)
        Text(
            "All detection, ANPR and evidence sealing run on this device. Network is opportunistic sync only.",
            style = HaloxTheme.typography.body,
            color = HaloxTheme.colors.inkMuted,
        )

        OutlinedTextField(
            value = state.officerId,
            onValueChange = viewModel::setOfficerId,
            label = { Text("Officer / Auditor ID") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.profile != null) {
            TierResultCard(tierName = state.effectiveTier.name, reasoning = state.tierReasoning)
        }

        if (state.requiredModels.isNotEmpty()) {
            HaloxCard {
                Text("MODELS TO PROVISION", style = HaloxTheme.typography.labelMicro, color = HaloxTheme.colors.inkFaint)
                Text(
                    "Downloaded + integrity-verified on first use. Supply hosted assets in ModelRegistry.",
                    style = HaloxTheme.typography.body,
                    color = HaloxTheme.colors.inkMuted,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                state.requiredModels.forEach { spec ->
                    MonoValue("${spec.kind.name.lowercase()}  ${spec.fileName}", modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }

        OperationalButton(
            text = "Start",
            onClick = { viewModel.complete(onComplete) },
            kind = ButtonKind.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

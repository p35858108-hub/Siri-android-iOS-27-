package com.assistant.siri.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.assistant.siri.R
import com.assistant.siri.ui.capsule.BlurSupport
import kotlin.math.roundToInt

/** Intensidad del desenfoque, luz del contorno de la cápsula y animaciones. */
@Composable
fun VisualSettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val blurSupported = BlurSupport.isSupported

    SettingsPage(title = stringResource(R.string.settings_visual), onBack = onBack) {
        SettingsSection(title = null) {
            SettingsSliderRow(
                title = stringResource(R.string.visual_blur_intensity),
                valueLabel = "",
                value = settings.blurIntensity,
                valueRange = 0f..1f,
                onValueChangeFinished = { value -> vm.updateSettings { it.copy(blurIntensity = value) } },
                enabled = blurSupported,
                labelFor = { "${(it * 100).roundToInt()} %" }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.visual_capsule_glow),
                summary = null,
                checked = settings.capsuleGlow,
                onCheckedChange = { value -> vm.updateSettings { it.copy(capsuleGlow = value) } }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.visual_reduce_motion),
                summary = null,
                checked = settings.reduceMotion,
                onCheckedChange = { value -> vm.updateSettings { it.copy(reduceMotion = value) } }
            )
        }

        if (!blurSupported) {
            Text(
                text = stringResource(R.string.visual_blur_unsupported),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

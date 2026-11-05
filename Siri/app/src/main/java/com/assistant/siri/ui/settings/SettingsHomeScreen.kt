package com.assistant.siri.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.assistant.siri.R

/** Inicio de la configuración: una fila por sección. */
@Composable
fun SettingsHomeScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val providers by vm.providers.collectAsStateWithLifecycle()
    val selectedId by vm.selectedProviderId.collectAsStateWithLifecycle()
    val active = providers?.let { list -> list.firstOrNull { it.id == selectedId } ?: list.firstOrNull() }

    val providersSummary = active?.let {
        stringResource(R.string.provider_summary_format, it.name, it.type.displayName)
    } ?: stringResource(R.string.provider_active_none)
    val modelsSummary = active?.modelId?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.settings_models_summary)

    SettingsPage(title = stringResource(R.string.settings_title), onBack = onBack) {
        SettingsSection(title = null) {
            SettingsRow(
                title = stringResource(R.string.settings_providers),
                summary = providersSummary,
                icon = Icons.Rounded.Cloud,
                onClick = { onNavigate(SettingsRoutes.PROVIDERS) },
                trailing = { Chevron() }
            )
            SettingsRow(
                title = stringResource(R.string.settings_models),
                summary = modelsSummary,
                icon = Icons.Rounded.AutoAwesome,
                onClick = { onNavigate(SettingsRoutes.MODELS) },
                trailing = { Chevron() }
            )
            SettingsRow(
                title = stringResource(R.string.settings_voice),
                summary = stringResource(R.string.settings_voice_summary),
                icon = Icons.Rounded.RecordVoiceOver,
                onClick = { onNavigate(SettingsRoutes.VOICE) },
                trailing = { Chevron() }
            )
            SettingsRow(
                title = stringResource(R.string.settings_visual),
                summary = stringResource(R.string.settings_visual_summary),
                icon = Icons.Rounded.Palette,
                onClick = { onNavigate(SettingsRoutes.VISUAL) },
                trailing = { Chevron() }
            )
            SettingsRow(
                title = stringResource(R.string.settings_app),
                summary = stringResource(R.string.settings_app_summary),
                icon = Icons.Rounded.Tune,
                onClick = { onNavigate(SettingsRoutes.APP) },
                trailing = { Chevron() }
            )
        }
    }
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

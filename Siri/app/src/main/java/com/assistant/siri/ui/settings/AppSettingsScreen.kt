package com.assistant.siri.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.assistant.siri.R

/** Asistente predeterminado, historial, restablecer ajustes y versión. */
@Composable
fun AppSettingsScreen(
    onBack: () -> Unit,
    onClearHistory: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "1.0"
    }

    SettingsPage(title = stringResource(R.string.settings_app), onBack = onBack) {
        SettingsSection(title = null) {
            SettingsRow(
                title = stringResource(R.string.app_default_assistant),
                summary = stringResource(R.string.app_default_assistant_summary),
                onClick = { openAssistantSettings(context) }
            )
            SettingsRow(
                title = stringResource(R.string.app_clear_history),
                summary = null,
                onClick = { confirmClear = true }
            )
            SettingsRow(
                title = stringResource(R.string.app_reset_settings),
                summary = stringResource(R.string.app_reset_settings_summary),
                onClick = { confirmReset = true }
            )
        }

        SettingsSection(title = stringResource(R.string.app_about)) {
            SettingsRow(
                title = stringResource(R.string.app_name),
                summary = stringResource(R.string.app_version, version),
                onClick = null
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.app_clear_history)) },
            text = { Text(stringResource(R.string.app_clear_history_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClearHistory()
                    }
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.app_reset_settings)) },
            text = { Text(stringResource(R.string.app_reset_settings_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        vm.resetSettings()
                    }
                ) {
                    Text(stringResource(R.string.action_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * Abre los ajustes de Android donde se elige el asistente predeterminado. La pantalla exacta
 * cambia según el fabricante, así que se prueban varias hasta encontrar una disponible.
 */
private fun openAssistantSettings(context: Context) {
    val actions = listOf(
        Settings.ACTION_VOICE_INPUT_SETTINGS,
        Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
        Settings.ACTION_SETTINGS
    )
    for (action in actions) {
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            continue
        }
    }
}

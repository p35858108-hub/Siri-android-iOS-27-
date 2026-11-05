package com.assistant.siri.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.assistant.siri.R
import com.assistant.siri.ai.userMessage
import com.assistant.siri.data.settings.DEFAULT_SYSTEM_PROMPT
import kotlinx.coroutines.launch

/** Máximo de modelos que se dibujan a la vez; el buscador acota el resto. */
private const val MAX_VISIBLE_MODELS = 80

/**
 * Modelo del proveedor activo (escrito a mano o elegido de la lista que ofrece el servicio)
 * e instrucciones del sistema.
 */
@Composable
fun ModelsScreen(
    onBack: () -> Unit,
    onOpenProviders: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val selectedId by vm.selectedProviderId.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val modelsState by vm.modelsState.collectAsStateWithLifecycle()

    val list = providers
    val active = list?.let { all -> all.firstOrNull { it.id == selectedId } ?: all.firstOrNull() }

    // La lista de modelos es del proveedor que la pidió
    LaunchedEffect(active?.id) { vm.clearModels() }

    SettingsPage(title = stringResource(R.string.settings_models), onBack = onBack) {
        if (list == null) return@SettingsPage

        if (active == null) {
            Text(
                text = stringResource(R.string.models_no_provider),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onOpenProviders) {
                Text(stringResource(R.string.settings_providers))
            }
        } else {
            // ----- Modelo actual -----
            var model by remember(active.id, active.modelId) { mutableStateOf(active.modelId) }
            var query by remember { mutableStateOf("") }

            SettingsSection(title = stringResource(R.string.models_current)) {
                SettingsRow(
                    title = active.name,
                    summary = active.type.displayName,
                    onClick = null
                )
            }

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text(stringResource(R.string.provider_model_id)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { scope.launch { vm.updateModel(active, model.trim()) } },
                enabled = model.isNotBlank() && model.trim() != active.modelId,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_save))
            }

            // ----- Lista de modelos del proveedor -----
            OutlinedButton(
                onClick = { vm.loadModels(active) },
                enabled = modelsState != ModelsState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.models_load))
            }

            when (val state = modelsState) {
                ModelsState.Idle -> Unit

                ModelsState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.models_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }

                is ModelsState.Failed -> Text(
                    text = state.error.userMessage(context),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )

                is ModelsState.Loaded -> {
                    if (state.models.isEmpty()) {
                        Text(
                            text = stringResource(R.string.models_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text(stringResource(R.string.models_search)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val filtered = state.models.filter { it.contains(query.trim(), ignoreCase = true) }
                        SettingsSection(title = stringResource(R.string.models_available)) {
                            filtered.take(MAX_VISIBLE_MODELS).forEach { id ->
                                val selected = id == active.modelId
                                SettingsRow(
                                    title = id,
                                    summary = if (selected) stringResource(R.string.models_selected) else null,
                                    onClick = {
                                        model = id
                                        scope.launch { vm.updateModel(active, id) }
                                    },
                                    trailing = {
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ----- Instrucciones del sistema -----
        var prompt by remember(settings.systemPrompt) { mutableStateOf(settings.systemPrompt) }

        SettingsSection(title = stringResource(R.string.system_prompt_title)) {
            Text(
                text = stringResource(R.string.system_prompt_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)
            )
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                minLines = 4,
                maxLines = 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { prompt = DEFAULT_SYSTEM_PROMPT },
                    enabled = prompt != DEFAULT_SYSTEM_PROMPT
                ) {
                    Text(stringResource(R.string.action_reset))
                }
                TextButton(
                    onClick = { vm.updateSettings { it.copy(systemPrompt = prompt) } },
                    enabled = prompt.isNotBlank() && prompt != settings.systemPrompt
                ) {
                    Text(
                        text = stringResource(R.string.action_save),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

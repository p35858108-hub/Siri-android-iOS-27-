package com.assistant.siri.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.assistant.siri.R
import com.assistant.siri.ai.AiProtocol
import com.assistant.siri.ai.ProviderConfig
import com.assistant.siri.ai.ProviderType
import com.assistant.siri.ai.userMessage
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

// ---------------------------------------------------------------------------------------------
// Lista de proveedores
// ---------------------------------------------------------------------------------------------

/** Proveedores guardados: el botón de la izquierda activa uno; tocar la fila lo edita. */
@Composable
fun ProvidersScreen(
    onBack: () -> Unit,
    onEdit: (String?) -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val providers by vm.providers.collectAsStateWithLifecycle()
    val selectedId by vm.selectedProviderId.collectAsStateWithLifecycle()

    SettingsPage(
        title = stringResource(R.string.settings_providers),
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(null) },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.provider_add)) }
            )
        }
    ) {
        val list = providers
        when {
            list == null -> Unit

            list.isEmpty() -> Text(
                text = stringResource(R.string.provider_none),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp)
            )

            else -> {
                SettingsSection(title = null) {
                    list.forEach { provider ->
                        val isActive = provider.id == selectedId
                        SettingsRow(
                            title = provider.name,
                            summary = listOf(provider.type.displayName, provider.modelId)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ") +
                                if (isActive) " · " + stringResource(R.string.provider_active) else "",
                            onClick = { onEdit(provider.id) },
                            trailing = {
                                RadioButton(
                                    selected = isActive,
                                    onClick = { vm.selectProvider(provider.id) }
                                )
                            }
                        )
                    }
                }
                // Espacio para que el botón flotante no tape la última fila
                Spacer(Modifier.height(72.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Crear o editar un proveedor
// ---------------------------------------------------------------------------------------------

/** Formulario de proveedor. Con [providerId] nulo se crea uno nuevo. */
@Composable
fun ProviderEditScreen(
    providerId: String?,
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val providers by vm.providers.collectAsStateWithLifecycle()
    val selectedId by vm.selectedProviderId.collectAsStateWithLifecycle()
    val list = providers ?: return

    val existing = list.firstOrNull { it.id == providerId }
    if (providerId != null && existing == null) {
        // El proveedor ya no existe (por ejemplo, se acaba de eliminar)
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val initial = existing ?: ProviderConfig(
        name = ProviderType.OPENAI.displayName,
        type = ProviderType.OPENAI,
        baseUrl = ProviderType.OPENAI.suggestedBaseUrl,
        modelId = "",
        protocol = ProviderType.OPENAI.defaultProtocol
    )

    ProviderForm(
        initial = initial,
        isNew = existing == null,
        isActive = existing != null && existing.id == selectedId,
        vm = vm,
        onBack = onBack
    )
}

@Composable
private fun ProviderForm(
    initial: ProviderConfig,
    isNew: Boolean,
    isActive: Boolean,
    vm: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val testState by vm.testState.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf(initial.name) }
    var type by remember { mutableStateOf(initial.type) }
    var protocol by remember { mutableStateOf(initial.protocol) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var modelId by remember { mutableStateOf(initial.modelId) }
    var showKey by remember { mutableStateOf(false) }
    var makeActive by remember { mutableStateOf(true) }
    var showErrors by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val draft = ProviderConfig(
        id = initial.id,
        name = name,
        type = type,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelId = modelId,
        protocol = protocol
    )

    val urlInvalid = baseUrl.trim().toHttpUrlOrNull() == null
    val modelMissing = modelId.isBlank()
    val keyMissing = protocol != AiProtocol.OPENAI && apiKey.isBlank()
    val hasProblem = urlInvalid || modelMissing || keyMissing

    // Un resultado de prueba deja de valer en cuanto cambia algo que le afecta
    LaunchedEffect(baseUrl, apiKey, modelId, protocol) { vm.clearTest() }
    DisposableEffect(Unit) { onDispose { vm.clearTest() } }

    fun changeType(newType: ProviderType) {
        val previous = type
        type = newType
        protocol = newType.defaultProtocol
        if (baseUrl.isBlank() || baseUrl.trim().trimEnd('/') == previous.suggestedBaseUrl) {
            baseUrl = newType.suggestedBaseUrl
        }
        if (name.isBlank() || name == previous.displayName) name = newType.displayName
    }

    SettingsPage(
        title = stringResource(if (isNew) R.string.provider_add else R.string.provider_edit),
        onBack = onBack
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.provider_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text(stringResource(R.string.provider_type), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProviderType.entries.forEach { option ->
                FilterChip(
                    selected = option == type,
                    onClick = { changeType(option) },
                    label = { Text(option.displayName) }
                )
            }
        }

        Text(stringResource(R.string.provider_protocol), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiProtocol.entries.forEach { option ->
                FilterChip(
                    selected = option == protocol,
                    onClick = { protocol = option },
                    label = {
                        Text(
                            stringResource(
                                when (option) {
                                    AiProtocol.OPENAI -> R.string.protocol_openai
                                    AiProtocol.GEMINI -> R.string.protocol_gemini
                                    AiProtocol.ANTHROPIC -> R.string.protocol_anthropic
                                }
                            )
                        )
                    }
                )
            }
        }

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text(stringResource(R.string.provider_base_url)) },
            singleLine = true,
            isError = showErrors && urlInvalid,
            supportingText = {
                Text(
                    stringResource(
                        if (showErrors && urlInvalid) R.string.error_invalid_url else R.string.provider_base_url_help
                    )
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text(stringResource(R.string.provider_api_key)) },
            singleLine = true,
            isError = showErrors && keyMissing,
            supportingText = {
                Text(
                    stringResource(
                        when {
                            showErrors && keyMissing -> R.string.error_no_api_key
                            protocol == AiProtocol.OPENAI -> R.string.provider_key_optional
                            else -> R.string.provider_key_note
                        }
                    )
                )
            },
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(
                            if (showKey) R.string.provider_hide_key else R.string.provider_show_key
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = modelId,
            onValueChange = { modelId = it },
            label = { Text(stringResource(R.string.provider_model_id)) },
            singleLine = true,
            isError = showErrors && modelMissing,
            supportingText = {
                if (showErrors && modelMissing) Text(stringResource(R.string.provider_model_required))
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth()
        )

        if (isNew) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.provider_select),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = makeActive, onCheckedChange = { makeActive = it })
            }
        }

        // Resultado de la prueba de conexión
        when (val result = testState) {
            ConnectionTestState.Idle -> Unit

            ConnectionTestState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.provider_testing),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            ConnectionTestState.Success -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.provider_test_ok),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            is ConnectionTestState.Failure -> Text(
                text = result.error.userMessage(context),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (saveFailed) {
            Text(
                text = stringResource(R.string.provider_save_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    if (hasProblem) {
                        showErrors = true
                    } else {
                        vm.testConnection(draft)
                    }
                },
                enabled = testState != ConnectionTestState.Running,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.provider_test))
            }
            Button(
                onClick = {
                    if (hasProblem) {
                        showErrors = true
                    } else {
                        scope.launch {
                            saving = true
                            saveFailed = false
                            val ok = vm.saveProvider(draft, isNew = isNew, makeActive = isNew && makeActive)
                            saving = false
                            if (ok) onBack() else saveFailed = true
                        }
                    }
                },
                enabled = !saving,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.action_save))
            }
        }

        if (!isNew) {
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.provider_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.provider_delete)) },
            text = { Text(stringResource(R.string.provider_delete_message, initial.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        vm.deleteProvider(initial.id)
                        onBack()
                    }
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

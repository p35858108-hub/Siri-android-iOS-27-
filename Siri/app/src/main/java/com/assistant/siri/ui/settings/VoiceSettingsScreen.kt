package com.assistant.siri.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.assistant.siri.R
import com.assistant.siri.voice.userMessage
import java.util.Locale

/** Idiomas que se ofrecen para reconocer y leer en voz alta. Vacío es el idioma del sistema. */
private val VoiceLanguages = listOf(
    "", "es-ES", "es-MX", "es-US", "es-AR", "en-US", "en-GB",
    "fr-FR", "de-DE", "it-IT", "pt-BR", "pt-PT", "ca-ES"
)

@Composable
private fun languageName(tag: String): String =
    if (tag.isBlank()) {
        stringResource(R.string.voice_language_system)
    } else {
        Locale.forLanguageTag(tag)
            .getDisplayName(Locale.getDefault())
            .replaceFirstChar { it.uppercase() }
    }

/** Lectura en voz alta, idioma, velocidad y tono, con prueba de voz. */
@Composable
fun VoiceSettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val speaking by vm.isSpeaking.collectAsStateWithLifecycle()
    val voiceError by vm.voiceError.collectAsStateWithLifecycle()
    var showLanguages by remember { mutableStateOf(false) }
    val sample = stringResource(R.string.voice_test_sample)

    // Si se sale de la pantalla, la voz de prueba se detiene
    DisposableEffect(Unit) { onDispose { vm.stopVoiceTest() } }

    SettingsPage(title = stringResource(R.string.settings_voice), onBack = onBack) {
        SettingsSection(title = null) {
            SettingsSwitchRow(
                title = stringResource(R.string.voice_speak_responses),
                summary = null,
                checked = settings.speakResponses,
                onCheckedChange = { value -> vm.updateSettings { it.copy(speakResponses = value) } }
            )
            SettingsRow(
                title = stringResource(R.string.voice_language),
                summary = languageName(settings.voiceLanguageTag),
                onClick = { showLanguages = true }
            )
            SettingsSliderRow(
                title = stringResource(R.string.voice_speech_rate),
                valueLabel = "",
                value = settings.speechRate,
                valueRange = 0.5f..2f,
                onValueChangeFinished = { value -> vm.updateSettings { it.copy(speechRate = value) } },
                labelFor = { String.format(Locale.getDefault(), "%.1f×", it) }
            )
            SettingsSliderRow(
                title = stringResource(R.string.voice_pitch),
                valueLabel = "",
                value = settings.speechPitch,
                valueRange = 0.5f..2f,
                onValueChangeFinished = { value -> vm.updateSettings { it.copy(speechPitch = value) } },
                labelFor = { String.format(Locale.getDefault(), "%.1f×", it) }
            )
        }

        OutlinedButton(
            onClick = { if (speaking) vm.stopVoiceTest() else vm.testVoice(sample) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(if (speaking) R.string.action_stop_speaking else R.string.voice_test))
        }

        voiceError?.let { error ->
            Text(
                text = error.userMessage(context),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    if (showLanguages) {
        // Si la etiqueta guardada no está en la lista, se muestra también
        val options = if (settings.voiceLanguageTag in VoiceLanguages) {
            VoiceLanguages
        } else {
            VoiceLanguages + settings.voiceLanguageTag
        }
        AlertDialog(
            onDismissRequest = { showLanguages = false },
            title = { Text(stringResource(R.string.voice_language)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { tag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.updateSettings { it.copy(voiceLanguageTag = tag) }
                                    showLanguages = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = tag == settings.voiceLanguageTag,
                                onClick = {
                                    vm.updateSettings { it.copy(voiceLanguageTag = tag) }
                                    showLanguages = false
                                }
                            )
                            Text(text = languageName(tag), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguages = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}

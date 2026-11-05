package com.assistant.siri.data.settings

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Lectura y escritura de las preferencias de la app. */
class AppPreferencesRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.safeData
        .map { it.toAppSettings() }
        .distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    /** Lee, transforma y guarda las preferencias en una sola operación atómica. */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            prefs.writeAppSettings(transform(prefs.toAppSettings()).normalized())
        }
    }

    suspend fun reset() {
        dataStore.edit { prefs -> prefs.writeAppSettings(AppSettings()) }
    }
}

private fun Preferences.toAppSettings(): AppSettings {
    val d = AppSettings()
    return AppSettings(
        speakResponses = this[SettingsKeys.SPEAK_RESPONSES] ?: d.speakResponses,
        voiceLanguageTag = this[SettingsKeys.VOICE_LANGUAGE_TAG] ?: d.voiceLanguageTag,
        speechRate = this[SettingsKeys.SPEECH_RATE] ?: d.speechRate,
        speechPitch = this[SettingsKeys.SPEECH_PITCH] ?: d.speechPitch,
        blurIntensity = this[SettingsKeys.BLUR_INTENSITY] ?: d.blurIntensity,
        capsuleGlow = this[SettingsKeys.CAPSULE_GLOW] ?: d.capsuleGlow,
        reduceMotion = this[SettingsKeys.REDUCE_MOTION] ?: d.reduceMotion,
        systemPrompt = this[SettingsKeys.SYSTEM_PROMPT] ?: d.systemPrompt
    ).normalized()
}

private fun MutablePreferences.writeAppSettings(s: AppSettings) {
    this[SettingsKeys.SPEAK_RESPONSES] = s.speakResponses
    this[SettingsKeys.VOICE_LANGUAGE_TAG] = s.voiceLanguageTag
    this[SettingsKeys.SPEECH_RATE] = s.speechRate
    this[SettingsKeys.SPEECH_PITCH] = s.speechPitch
    this[SettingsKeys.BLUR_INTENSITY] = s.blurIntensity
    this[SettingsKeys.CAPSULE_GLOW] = s.capsuleGlow
    this[SettingsKeys.REDUCE_MOTION] = s.reduceMotion
    this[SettingsKeys.SYSTEM_PROMPT] = s.systemPrompt
}

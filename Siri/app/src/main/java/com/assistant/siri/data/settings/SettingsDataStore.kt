package com.assistant.siri.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException

/** DataStore principal: perfiles de proveedor, selección y preferencias. */
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Claves de preferencias. */
internal object SettingsKeys {
    // Proveedores
    val PROVIDER_PROFILES = stringPreferencesKey("provider_profiles")
    val SELECTED_PROVIDER_ID = stringPreferencesKey("selected_provider_id")

    // Voz
    val SPEAK_RESPONSES = booleanPreferencesKey("voice_speak_responses")
    val VOICE_LANGUAGE_TAG = stringPreferencesKey("voice_language_tag")
    val SPEECH_RATE = floatPreferencesKey("voice_speech_rate")
    val SPEECH_PITCH = floatPreferencesKey("voice_speech_pitch")

    // Apariencia
    val BLUR_INTENSITY = floatPreferencesKey("visual_blur_intensity")
    val CAPSULE_GLOW = booleanPreferencesKey("visual_capsule_glow")
    val REDUCE_MOTION = booleanPreferencesKey("visual_reduce_motion")

    // Asistente
    val SYSTEM_PROMPT = stringPreferencesKey("assistant_system_prompt")
}

/** Lectura que no falla si el archivo no se puede leer: devuelve preferencias vacías. */
internal val DataStore<Preferences>.safeData: Flow<Preferences>
    get() = data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

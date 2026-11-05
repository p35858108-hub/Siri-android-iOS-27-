package com.assistant.siri.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.assistant.siri.ai.ProviderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Gestiona los perfiles de proveedor: crear, editar, eliminar y seleccionar.
 * Los perfiles se guardan en DataStore sin la clave; la clave va cifrada en [SecureKeyStore].
 */
class ProviderRepository(
    context: Context,
    private val secureKeyStore: SecureKeyStore
) {

    private val dataStore = context.applicationContext.settingsDataStore

    private val profiles: Flow<List<ProviderConfig>> = dataStore.safeData
        .map { decodeProfiles(it[SettingsKeys.PROVIDER_PROFILES]) }
        .distinctUntilChanged()

    /** Todos los perfiles, ya con su clave de API descifrada. */
    val providers: Flow<List<ProviderConfig>> =
        combine(profiles, secureKeyStore.keys) { list, keys ->
            list.map { it.copy(apiKey = keys[it.id].orEmpty()) }
        }

    val selectedProviderId: Flow<String?> = dataStore.safeData
        .map { it[SettingsKeys.SELECTED_PROVIDER_ID] }
        .distinctUntilChanged()

    /** Perfil seleccionado (o el primero, si la selección no existe). Null si no hay perfiles. */
    val selectedProvider: Flow<ProviderConfig?> =
        combine(providers, selectedProviderId) { list, selectedId ->
            list.firstOrNull { it.id == selectedId } ?: list.firstOrNull()
        }

    suspend fun getSelectedProvider(): ProviderConfig? = selectedProvider.first()

    suspend fun getProvider(id: String): ProviderConfig? =
        providers.first().firstOrNull { it.id == id }

    /**
     * Crea un perfil. Si no había ninguno seleccionado, lo selecciona.
     * Puede lanzar [SecureStorageException] si no se puede cifrar la clave.
     */
    suspend fun create(config: ProviderConfig): ProviderConfig {
        val clean = config.sanitized()
        // Primero la clave, para que el perfil nunca aparezca sin ella
        secureKeyStore.save(clean.id, clean.apiKey)
        dataStore.edit { prefs ->
            val list = decodeProfiles(prefs[SettingsKeys.PROVIDER_PROFILES])
                .filterNot { it.id == clean.id } + clean
            prefs[SettingsKeys.PROVIDER_PROFILES] = encodeProfiles(list)

            val selected = prefs[SettingsKeys.SELECTED_PROVIDER_ID]
            if (selected == null || list.none { it.id == selected }) {
                prefs[SettingsKeys.SELECTED_PROVIDER_ID] = clean.id
            }
        }
        return clean
    }

    /**
     * Edita un perfil existente (identificado por su id).
     * Guarda la clave tal cual llega: si está vacía, se elimina.
     * Devuelve false si el perfil no existe.
     */
    suspend fun update(config: ProviderConfig): Boolean {
        val clean = config.sanitized()
        val exists = profiles.first().any { it.id == clean.id }
        if (!exists) return false

        secureKeyStore.save(clean.id, clean.apiKey)
        dataStore.edit { prefs ->
            val list = decodeProfiles(prefs[SettingsKeys.PROVIDER_PROFILES])
                .map { if (it.id == clean.id) clean else it }
            prefs[SettingsKeys.PROVIDER_PROFILES] = encodeProfiles(list)
        }
        return true
    }

    /** Elimina un perfil y su clave. Si era el seleccionado, selecciona otro. */
    suspend fun delete(id: String) {
        dataStore.edit { prefs ->
            val list = decodeProfiles(prefs[SettingsKeys.PROVIDER_PROFILES])
                .filterNot { it.id == id }
            prefs[SettingsKeys.PROVIDER_PROFILES] = encodeProfiles(list)

            if (prefs[SettingsKeys.SELECTED_PROVIDER_ID] == id) {
                val next = list.firstOrNull()
                if (next != null) {
                    prefs[SettingsKeys.SELECTED_PROVIDER_ID] = next.id
                } else {
                    prefs.remove(SettingsKeys.SELECTED_PROVIDER_ID)
                }
            }
        }
        secureKeyStore.delete(id)
    }

    /** Selecciona el perfil activo. Devuelve false si el perfil no existe. */
    suspend fun select(id: String): Boolean {
        var found = false
        dataStore.edit { prefs ->
            val exists = decodeProfiles(prefs[SettingsKeys.PROVIDER_PROFILES]).any { it.id == id }
            if (exists) {
                prefs[SettingsKeys.SELECTED_PROVIDER_ID] = id
                found = true
            }
        }
        return found
    }

    private fun ProviderConfig.sanitized(): ProviderConfig = copy(
        name = name.trim().ifBlank { type.displayName },
        baseUrl = baseUrl.trim().trimEnd('/'),
        apiKey = apiKey.trim(),
        modelId = modelId.trim()
    )

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val listSerializer = ListSerializer(ProviderConfig.serializer())

        fun encodeProfiles(list: List<ProviderConfig>): String =
            json.encodeToString(listSerializer, list)

        fun decodeProfiles(raw: String?): List<ProviderConfig> {
            if (raw.isNullOrBlank()) return emptyList()
            return try {
                json.decodeFromString(listSerializer, raw)
            } catch (e: IllegalArgumentException) {
                emptyList()
            }
        }
    }
}

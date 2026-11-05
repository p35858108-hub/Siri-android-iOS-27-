package com.assistant.siri.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.assistant.siri.ai.AiError
import com.assistant.siri.ai.AiException
import com.assistant.siri.ai.AiProviderFactory
import com.assistant.siri.ai.ModelCatalog
import com.assistant.siri.ai.ProviderConfig
import com.assistant.siri.appContainer
import com.assistant.siri.data.settings.AppSettings
import com.assistant.siri.data.settings.SecureStorageException
import com.assistant.siri.voice.SpeechException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Resultado de probar la conexión con un proveedor. */
sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Running : ConnectionTestState
    data object Success : ConnectionTestState
    data class Failure(val error: AiException) : ConnectionTestState
}

/** Estado de la lista de modelos que ofrece el proveedor. */
sealed interface ModelsState {
    data object Idle : ModelsState
    data object Loading : ModelsState
    data class Loaded(val models: List<String>) : ModelsState
    data class Failed(val error: AiException) : ModelsState
}

/** Datos y acciones de las pantallas de configuración. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val providerRepository = container.providerRepository
    private val preferences = container.preferencesRepository
    private val synthesizer = container.speechSynthesizer

    /** Proveedores guardados. Null mientras se cargan. */
    val providers: StateFlow<List<ProviderConfig>?> = providerRepository.providers
        .map<List<ProviderConfig>, List<ProviderConfig>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Id del proveedor activo. */
    val selectedProviderId: StateFlow<String?> = providerRepository.selectedProvider
        .map { it?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val settings: StateFlow<AppSettings> = preferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings())

    val isSpeaking: StateFlow<Boolean> = synthesizer.isSpeaking

    // ---------- Proveedores ----------

    /**
     * Crea o edita un proveedor. Devuelve false si no se pudo guardar la clave de forma
     * segura (Keystore).
     */
    suspend fun saveProvider(config: ProviderConfig, isNew: Boolean, makeActive: Boolean): Boolean =
        try {
            if (isNew) providerRepository.create(config) else providerRepository.update(config)
            if (makeActive) providerRepository.select(config.id)
            true
        } catch (e: SecureStorageException) {
            false
        }

    /** Cambia solo el modelo de un proveedor. Devuelve false si no se pudo guardar. */
    suspend fun updateModel(config: ProviderConfig, modelId: String): Boolean =
        try {
            providerRepository.update(config.copy(modelId = modelId))
        } catch (e: SecureStorageException) {
            false
        }

    fun deleteProvider(id: String) {
        viewModelScope.launch { providerRepository.delete(id) }
    }

    fun selectProvider(id: String) {
        viewModelScope.launch { providerRepository.select(id) }
    }

    // ---------- Probar la conexión ----------

    private val _testState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val testState: StateFlow<ConnectionTestState> = _testState.asStateFlow()
    private var testJob: Job? = null

    fun testConnection(config: ProviderConfig) {
        testJob?.cancel()
        _testState.value = ConnectionTestState.Running
        testJob = viewModelScope.launch {
            _testState.value = try {
                AiProviderFactory.create(config.protocol).testConnection(config)
                ConnectionTestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                ConnectionTestState.Failure(e)
            } catch (e: Exception) {
                ConnectionTestState.Failure(AiException(AiError.UNKNOWN, cause = e))
            }
        }
    }

    fun clearTest() {
        testJob?.cancel()
        _testState.value = ConnectionTestState.Idle
    }

    // ---------- Lista de modelos ----------

    private val _modelsState = MutableStateFlow<ModelsState>(ModelsState.Idle)
    val modelsState: StateFlow<ModelsState> = _modelsState.asStateFlow()
    private var modelsJob: Job? = null

    fun loadModels(config: ProviderConfig) {
        modelsJob?.cancel()
        _modelsState.value = ModelsState.Loading
        modelsJob = viewModelScope.launch {
            _modelsState.value = try {
                ModelsState.Loaded(ModelCatalog.fetch(config))
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                ModelsState.Failed(e)
            } catch (e: Exception) {
                ModelsState.Failed(AiException(AiError.UNKNOWN, cause = e))
            }
        }
    }

    fun clearModels() {
        modelsJob?.cancel()
        _modelsState.value = ModelsState.Idle
    }

    // ---------- Preferencias ----------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { preferences.update(transform) }
    }

    fun resetSettings() {
        viewModelScope.launch { preferences.reset() }
    }

    // ---------- Probar la voz ----------

    private val _voiceError = MutableStateFlow<SpeechException?>(null)
    val voiceError: StateFlow<SpeechException?> = _voiceError.asStateFlow()
    private var voiceJob: Job? = null

    fun testVoice(sample: String) {
        voiceJob?.cancel()
        voiceJob = viewModelScope.launch {
            _voiceError.value = null
            val voice = preferences.current()
            try {
                synthesizer.speak(sample, voice.voiceLanguageTag, voice.speechRate, voice.speechPitch)
            } catch (e: SpeechException) {
                _voiceError.value = e
            }
        }
    }

    fun stopVoiceTest() {
        voiceJob?.cancel()
        voiceJob = null
        synthesizer.stop()
    }

    override fun onCleared() {
        synthesizer.stop()
        super.onCleared()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

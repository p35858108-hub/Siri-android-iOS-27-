package com.assistant.siri.assistant

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.assistant.siri.ai.AiException
import com.assistant.siri.ai.AiMessage
import com.assistant.siri.ai.AiProviderFactory
import com.assistant.siri.ai.AiRole
import com.assistant.siri.appContainer
import com.assistant.siri.data.chat.ChatMessage
import com.assistant.siri.data.chat.ChatRole
import com.assistant.siri.data.chat.ConversationSummary
import com.assistant.siri.data.settings.AppSettings
import com.assistant.siri.voice.SpeechEvent
import com.assistant.siri.voice.SpeechException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Cerebro del asistente. Gestiona los estados reposo, escucha, escritura, procesando,
 * respuesta y error; mantiene el contexto de la conversación y permite cancelar.
 *
 * Todas las funciones públicas se llaman desde el hilo principal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val conversations = container.conversationRepository
    private val providers = container.providerRepository
    private val preferences = container.preferencesRepository
    private val recognizer = container.speechRecognizerManager
    private val synthesizer = container.speechSynthesizer

    // ---------- Estado público ----------

    private val _state = MutableStateFlow<AssistantState>(AssistantState.Idle)

    /** Estado actual de la cápsula. */
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    private val _conversationId = MutableStateFlow<Long?>(null)

    /** Conversación en curso. Null hasta que se envía el primer mensaje. */
    val conversationId: StateFlow<Long?> = _conversationId.asStateFlow()

    /** Mensajes de la conversación en curso (se actualizan mientras llega la respuesta). */
    val messages: StateFlow<List<ChatMessage>> = _conversationId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else conversations.observeMessages(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Historial de conversaciones. */
    val history: StateFlow<List<ConversationSummary>> = conversations.conversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Preferencias de la app (apariencia, voz...). */
    val settings: StateFlow<AppSettings> = preferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings())

    /** True si hay un proveedor de IA configurado. */
    val hasProvider: StateFlow<Boolean> = providers.selectedProvider
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

    /** True mientras se lee una respuesta en voz alta. */
    val isSpeaking: StateFlow<Boolean> = synthesizer.isSpeaking

    private val _events = MutableSharedFlow<AssistantEvent>(extraBufferCapacity = 8)

    /** Avisos puntuales (no cambian el estado). */
    val events: SharedFlow<AssistantEvent> = _events.asSharedFlow()

    // ---------- Trabajos en curso ----------

    private var listenJob: Job? = null
    private var requestJob: Job? = null
    private var speakJob: Job? = null

    /** Acción que ejecuta `retry()` tras un error. */
    private var retryAction: (() -> Unit)? = null

    init {
        // Limpia conversaciones vacías que hayan quedado abandonadas
        viewModelScope.launch { conversations.deleteEmptyConversations() }
    }

    // ---------- Voz ----------

    /** Empieza a escuchar. Cancela cualquier otra cosa en curso. */
    fun startListening() {
        cancelWork()
        retryAction = null
        _state.value = AssistantState.Listening()

        listenJob = viewModelScope.launch {
            val voiceLanguage = preferences.current().voiceLanguageTag
            var finalText: String? = null
            try {
                recognizer.listen(voiceLanguage).collect { event ->
                    when (event) {
                        is SpeechEvent.Level -> _state.update { current ->
                            if (current is AssistantState.Listening) current.copy(level = event.value) else current
                        }

                        is SpeechEvent.Partial -> _state.update { current ->
                            if (current is AssistantState.Listening) current.copy(partialText = event.text) else current
                        }

                        is SpeechEvent.Final -> finalText = event.text

                        SpeechEvent.Ready, SpeechEvent.VoiceDetected -> Unit
                    }
                }
            } catch (e: SpeechException) {
                fail(AssistantError.Speech(e)) { startListening() }
                return@launch
            }

            val text = finalText
            if (text != null) {
                beginRequest(text)
            } else {
                _state.value = AssistantState.Idle
            }
        }
    }

    /** Deja de escuchar y procesa lo que se haya oído hasta ahora. */
    fun stopListening() {
        if (_state.value is AssistantState.Listening) recognizer.stopListening()
    }

    /** Interrumpe la lectura en voz alta. */
    fun stopSpeaking() {
        speakJob?.cancel()
        speakJob = null
        synthesizer.stop()
    }

    /** Lee en voz alta la respuesta que se está mostrando. */
    fun speakResponse() {
        val current = _state.value as? AssistantState.Responding ?: return
        if (current.isStreaming || current.text.isBlank()) return
        speak(current.text)
    }

    // ---------- Escritura ----------

    /** Abre el modo de escritura dentro de la cápsula. */
    fun startTyping(initialText: String = "") {
        cancelWork()
        retryAction = null
        _state.value = AssistantState.Typing(initialText)
    }

    fun updateDraft(text: String) {
        _state.update { current ->
            if (current is AssistantState.Typing) current.copy(draft = text) else current
        }
    }

    /** Envía el texto escrito. */
    fun submitDraft() {
        val current = _state.value as? AssistantState.Typing ?: return
        if (current.draft.isNotBlank()) submit(current.draft)
    }

    // ---------- Peticiones ----------

    /** Envía un mensaje al modelo dentro de la conversación en curso. */
    fun submit(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        cancelWork()
        beginRequest(clean)
    }

    /**
     * Cancela lo que esté en curso.
     * Si se estaba escribiendo la respuesta, se conserva el texto recibido hasta ese momento.
     */
    fun cancel() {
        val current = _state.value
        cancelWork()
        retryAction = null
        _state.value = if (current is AssistantState.Responding && current.text.isNotBlank()) {
            current.copy(isStreaming = false)
        } else {
            AssistantState.Idle
        }
    }

    /** Cierra la cápsula y vuelve al reposo, deteniendo cualquier trabajo y la lectura en voz alta. */
    fun dismiss() {
        cancelWork()
        retryAction = null
        _state.value = AssistantState.Idle
    }

    /** Repite la última acción fallida. */
    fun retry() {
        val action = retryAction ?: return
        retryAction = null
        action()
    }

    // ---------- Conversaciones ----------

    /** Empieza una conversación nueva (se crea al enviar el primer mensaje). */
    fun newConversation() {
        cancelWork()
        retryAction = null
        _conversationId.value = null
        _state.value = AssistantState.Idle
    }

    /** Abre una conversación del historial para seguir con ella. */
    fun openConversation(id: Long) {
        cancelWork()
        retryAction = null
        _conversationId.value = id
        _state.value = AssistantState.Idle
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            if (_conversationId.value == id) newConversation()
            conversations.deleteConversation(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            newConversation()
            conversations.deleteAllConversations()
        }
    }

    // ---------- Lógica interna ----------

    private fun beginRequest(userText: String) {
        requestJob = viewModelScope.launch {
            _state.value = AssistantState.Processing(userText)
            val conversationId = try {
                val id = _conversationId.value ?: conversations.createConversation().also {
                    _conversationId.value = it
                }
                conversations.addMessage(id, ChatRole.USER, userText)
                id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(AssistantError.Unknown) { submit(userText) }
                return@launch
            }
            runStream(conversationId, userText)
        }
    }

    /** Repite la petición sin volver a guardar el mensaje del usuario. */
    private fun retryStream(conversationId: Long, userText: String) {
        cancelWork()
        requestJob = viewModelScope.launch { runStream(conversationId, userText) }
    }

    private suspend fun runStream(conversationId: Long, userText: String) {
        _state.value = AssistantState.Processing(userText)

        val retry: () -> Unit = { retryStream(conversationId, userText) }

        val provider = providers.getSelectedProvider()
        if (provider == null) {
            fail(AssistantError.NoProvider, retry)
            return
        }
        val currentSettings = preferences.current()

        val text = StringBuilder()
        var assistantMessageId: Long? = null
        var lastPersist = 0L

        // Guarda la respuesta en la base de datos (la crea la primera vez)
        suspend fun persist() {
            if (text.isEmpty()) return
            val content = text.toString()
            val id = assistantMessageId
            if (id == null) {
                assistantMessageId = conversations.addMessage(conversationId, ChatRole.ASSISTANT, content)
            } else {
                conversations.updateMessageContent(id, content)
            }
            lastPersist = SystemClock.elapsedRealtime()
        }

        // Error durante la respuesta: si ya hay texto se conserva, si no se muestra el error
        fun onFailure(error: AssistantError) {
            if (text.isNotBlank()) {
                _state.value = AssistantState.Responding(userText, text.toString(), isStreaming = false)
                _events.tryEmit(AssistantEvent.Problem(error))
            } else {
                fail(error, retry)
            }
        }

        try {
            val history = conversations.getContext(conversationId)
                // Los modelos exigen que la conversación empiece por un mensaje del usuario
                .dropWhile { it.role == AiRole.ASSISTANT }
            val request = buildList {
                if (currentSettings.systemPrompt.isNotBlank()) {
                    add(AiMessage(AiRole.SYSTEM, currentSettings.systemPrompt))
                }
                addAll(history)
            }

            AiProviderFactory.forConfig(provider).stream(provider, request).collect { chunk ->
                currentCoroutineContext().ensureActive()
                text.append(chunk)
                val now = SystemClock.elapsedRealtime()
                if (assistantMessageId == null || now - lastPersist >= PERSIST_INTERVAL_MS) {
                    persist()
                }
                _state.value = AssistantState.Responding(userText, text.toString(), isStreaming = true)
            }

            _state.value = AssistantState.Responding(userText, text.toString(), isStreaming = false)
            retryAction = null
            if (currentSettings.speakResponses) speak(text.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiException) {
            onFailure(AssistantError.Ai(e))
        } catch (e: Exception) {
            onFailure(AssistantError.Unknown)
        } finally {
            // Guarda el texto final aunque la petición se haya cancelado
            withContext(NonCancellable) {
                runCatching { persist() }
            }
        }
    }

    private fun speak(text: String) {
        speakJob?.cancel()
        speakJob = viewModelScope.launch {
            val voice = preferences.current()
            try {
                synthesizer.speak(text, voice.voiceLanguageTag, voice.speechRate, voice.speechPitch)
            } catch (e: SpeechException) {
                _events.tryEmit(AssistantEvent.Problem(AssistantError.Speech(e)))
            }
        }
    }

    private fun fail(error: AssistantError, retry: (() -> Unit)?) {
        retryAction = retry
        _state.value = AssistantState.Error(error, canRetry = retry != null)
    }

    /** Cancela escucha, petición y lectura en voz alta. */
    private fun cancelWork() {
        listenJob?.cancel()
        requestJob?.cancel()
        speakJob?.cancel()
        listenJob = null
        requestJob = null
        speakJob = null
        synthesizer.stop()
    }

    override fun onCleared() {
        synthesizer.stop()
        super.onCleared()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** Cada cuánto se guarda la respuesta parcial mientras llega. */
        const val PERSIST_INTERVAL_MS = 400L
    }
}

package com.assistant.siri.assistant

import android.content.Context
import com.assistant.siri.R
import com.assistant.siri.ai.AiException
import com.assistant.siri.ai.userMessage
import com.assistant.siri.voice.SpeechException
import com.assistant.siri.voice.userMessage

/** Estado visible del asistente: lo que debe mostrar la cápsula. */
sealed interface AssistantState {

    /** Reposo: solo la cápsula negra alrededor de la cámara. */
    data object Idle : AssistantState

    /** Escuchando. [level] va de 0.0 a 1.0 y [partialText] es lo que se lleva reconocido. */
    data class Listening(
        val level: Float = 0f,
        val partialText: String = ""
    ) : AssistantState

    /** Escribiendo dentro de la cápsula. */
    data class Typing(val draft: String = "") : AssistantState

    /** Pensando: la pregunta ya se envió y aún no llega texto. */
    data class Processing(val userText: String) : AssistantState

    /** Respuesta. Con [isStreaming] en true el texto sigue creciendo. */
    data class Responding(
        val userText: String,
        val text: String,
        val isStreaming: Boolean
    ) : AssistantState

    /** Error. Con [canRetry] se puede llamar a `retry()`. */
    data class Error(
        val error: AssistantError,
        val canRetry: Boolean
    ) : AssistantState
}

/** Causa de un error del asistente. */
sealed interface AssistantError {
    /** No hay ningún proveedor de IA configurado. */
    data object NoProvider : AssistantError

    data class Ai(val exception: AiException) : AssistantError

    data class Speech(val exception: SpeechException) : AssistantError

    data object Unknown : AssistantError
}

/** Avisos puntuales que no cambian el estado (por ejemplo, fallo al leer en voz alta). */
sealed interface AssistantEvent {
    data class Problem(val error: AssistantError) : AssistantEvent
}

/** Texto legible para mostrar al usuario. */
fun AssistantError.userMessage(context: Context): String = when (this) {
    AssistantError.NoProvider -> context.getString(R.string.error_no_provider)
    is AssistantError.Ai -> exception.userMessage(context)
    is AssistantError.Speech -> exception.userMessage(context)
    AssistantError.Unknown -> context.getString(R.string.error_unknown)
}

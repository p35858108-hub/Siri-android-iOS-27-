package com.assistant.siri.data.chat

import com.assistant.siri.ai.AiMessage
import com.assistant.siri.ai.AiRole

enum class ChatRole {
    USER,
    ASSISTANT
}

/** Mensaje de una conversación. */
data class ChatMessage(
    val id: Long,
    val conversationId: Long,
    val role: ChatRole,
    val content: String,
    val createdAt: Long
)

/** Conversación. Un título vacío significa "sin título" (usa R.string.conversation_untitled). */
data class Conversation(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

/** Fila del historial: conversación con vista previa de su último mensaje. */
data class ConversationSummary(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessage: String?
)

/** Convierte un mensaje guardado en un mensaje para enviar al modelo. */
fun ChatMessage.toAiMessage(): AiMessage = AiMessage(
    role = if (role == ChatRole.USER) AiRole.USER else AiRole.ASSISTANT,
    content = content
)

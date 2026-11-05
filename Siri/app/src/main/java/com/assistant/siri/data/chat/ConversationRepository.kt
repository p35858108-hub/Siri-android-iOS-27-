package com.assistant.siri.data.chat

import androidx.room.withTransaction
import com.assistant.siri.ai.AiMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Acceso a conversaciones y mensajes.
 * Crear, eliminar y consultar el historial; el título se genera con el primer mensaje del usuario.
 */
class ConversationRepository(private val database: ChatDatabase) {

    private val dao = database.conversationDao()

    // ---------- Consultas ----------

    /** Historial de conversaciones (solo las que tienen mensajes), la más reciente primero. */
    val conversations: Flow<List<ConversationSummary>> = dao.observeConversationSummaries()

    fun observeConversation(id: Long): Flow<Conversation?> =
        dao.observeConversation(id).map { it?.toConversation() }

    suspend fun getConversation(id: Long): Conversation? =
        dao.getConversation(id)?.toConversation()

    fun observeMessages(conversationId: Long): Flow<List<ChatMessage>> =
        dao.observeMessages(conversationId).map { list -> list.map { it.toChatMessage() } }

    suspend fun getMessages(conversationId: Long): List<ChatMessage> =
        dao.getMessages(conversationId).map { it.toChatMessage() }

    /** Id de la conversación más reciente que tenga mensajes, o null si no hay ninguna. */
    suspend fun getMostRecentConversationId(): Long? = dao.getMostRecentConversationId()

    /**
     * Contexto para el modelo: los últimos [limit] mensajes de la conversación,
     * en orden cronológico y ya convertidos a [AiMessage].
     */
    suspend fun getContext(conversationId: Long, limit: Int = DEFAULT_CONTEXT_LIMIT): List<AiMessage> =
        dao.getLastMessages(conversationId, limit.coerceAtLeast(1))
            .map { it.toChatMessage().toAiMessage() }

    // ---------- Altas y cambios ----------

    /** Crea una conversación vacía y devuelve su id. No aparece en el historial hasta tener mensajes. */
    suspend fun createConversation(title: String = ""): Long {
        val now = System.currentTimeMillis()
        return dao.insertConversation(
            ConversationEntity(title = title.trim(), createdAt = now, updatedAt = now)
        )
    }

    /**
     * Añade un mensaje y actualiza la fecha de la conversación.
     * Si es el primer mensaje del usuario y no hay título, lo usa como título.
     * Devuelve el id del mensaje. La conversación debe existir.
     */
    suspend fun addMessage(conversationId: Long, role: ChatRole, content: String): Long =
        database.withTransaction {
            val now = System.currentTimeMillis()
            val messageId = dao.insertMessage(
                MessageEntity(
                    conversationId = conversationId,
                    role = role,
                    content = content,
                    createdAt = now
                )
            )
            dao.updateTimestamp(conversationId, now)

            if (role == ChatRole.USER) {
                val conversation = dao.getConversation(conversationId)
                if (conversation != null && conversation.title.isBlank()) {
                    dao.updateTitle(conversationId, titleFrom(content))
                }
            }
            messageId
        }

    /** Reemplaza el texto de un mensaje (por ejemplo, al terminar o cancelar una respuesta). */
    suspend fun updateMessageContent(messageId: Long, content: String) {
        dao.updateMessageContent(messageId, content)
    }

    suspend fun renameConversation(id: Long, title: String) {
        dao.updateTitle(id, title.trim())
    }

    // ---------- Bajas ----------

    /** Elimina la conversación y todos sus mensajes. */
    suspend fun deleteConversation(id: Long) {
        dao.deleteConversation(id)
    }

    suspend fun deleteAllConversations() {
        dao.deleteAllConversations()
    }

    /**
     * Elimina conversaciones sin mensajes (por ejemplo, las que se crearon y se abandonaron).
     * Llamar antes de crear una nueva, no después.
     */
    suspend fun deleteEmptyConversations() {
        dao.deleteEmptyConversations()
    }

    // ---------- Utilidades ----------

    private fun titleFrom(text: String): String {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val collapsed = firstLine.trim().replace(WHITESPACE, " ")
        return if (collapsed.length > MAX_TITLE_LENGTH) {
            collapsed.take(MAX_TITLE_LENGTH).trimEnd() + "…"
        } else {
            collapsed
        }
    }

    private fun ConversationEntity.toConversation() = Conversation(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun MessageEntity.toChatMessage() = ChatMessage(
        id = id,
        conversationId = conversationId,
        role = role,
        content = content,
        createdAt = createdAt
    )

    private companion object {
        const val DEFAULT_CONTEXT_LIMIT = 20
        const val MAX_TITLE_LENGTH = 60
        val WHITESPACE = Regex("\\s+")
    }
}

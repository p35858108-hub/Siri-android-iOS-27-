package com.assistant.siri.data.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    // ---------- Altas ----------

    @Insert
    suspend fun insertConversation(entity: ConversationEntity): Long

    @Insert
    suspend fun insertMessage(entity: MessageEntity): Long

    // ---------- Consultas ----------

    /** Historial: solo conversaciones con al menos un mensaje, de más reciente a más antigua. */
    @Query(
        """
        SELECT c.id AS id,
               c.title AS title,
               c.createdAt AS createdAt,
               c.updatedAt AS updatedAt,
               (SELECT m.content FROM messages m
                WHERE m.conversationId = c.id
                ORDER BY m.createdAt DESC, m.id DESC
                LIMIT 1) AS lastMessage
        FROM conversations c
        WHERE EXISTS (SELECT 1 FROM messages m2 WHERE m2.conversationId = c.id)
        ORDER BY c.updatedAt DESC
        """
    )
    fun observeConversationSummaries(): Flow<List<ConversationSummary>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversation(id: Long): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeConversation(id: Long): Flow<ConversationEntity?>

    @Query(
        """
        SELECT id FROM conversations
        WHERE EXISTS (SELECT 1 FROM messages m WHERE m.conversationId = conversations.id)
        ORDER BY updatedAt DESC
        LIMIT 1
        """
    )
    suspend fun getMostRecentConversationId(): Long?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessages(conversationId: Long): List<MessageEntity>

    /** Los últimos [limit] mensajes, en orden cronológico. */
    @Query(
        """
        SELECT * FROM (
            SELECT * FROM messages
            WHERE conversationId = :conversationId
            ORDER BY createdAt DESC, id DESC
            LIMIT :limit
        )
        ORDER BY createdAt ASC, id ASC
        """
    )
    suspend fun getLastMessages(conversationId: Long, limit: Int): List<MessageEntity>

    // ---------- Cambios ----------

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTimestamp(id: Long, updatedAt: Long)

    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateMessageContent(id: Long, content: String)

    // ---------- Bajas ----------

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: Long)

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()

    @Query("DELETE FROM conversations WHERE id NOT IN (SELECT DISTINCT conversationId FROM messages)")
    suspend fun deleteEmptyConversations()
}

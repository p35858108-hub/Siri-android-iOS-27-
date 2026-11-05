package com.assistant.siri.data.chat

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: ChatRole,
    val content: String,
    val createdAt: Long
)

class ChatConverters {

    @TypeConverter
    fun roleToString(role: ChatRole): String = role.name

    @TypeConverter
    fun stringToRole(value: String): ChatRole =
        try {
            ChatRole.valueOf(value)
        } catch (e: IllegalArgumentException) {
            ChatRole.ASSISTANT
        }
}

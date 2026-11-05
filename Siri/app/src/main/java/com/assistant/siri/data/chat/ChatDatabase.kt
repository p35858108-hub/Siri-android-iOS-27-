package com.assistant.siri.data.chat

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(ChatConverters::class)
abstract class ChatDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao

    companion object {
        private const val DATABASE_NAME = "chat.db"

        fun create(context: Context): ChatDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ChatDatabase::class.java,
                DATABASE_NAME
            ).build()
    }
}

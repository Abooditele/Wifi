package com.example.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.db.entity.MessageEntity
import com.example.data.db.entity.MessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationDeviceId = :conversationDeviceId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationDeviceId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationDeviceId = :conversationDeviceId AND (content LIKE '%' || :query || '%' OR mediaName LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun searchMessagesInConversation(conversationDeviceId: String, query: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageId = :id")
    suspend fun getMessageById(id: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE conversationDeviceId = :conversationDeviceId ORDER BY timestamp DESC LIMIT 1")
    fun observeLastMessageForConversation(conversationDeviceId: String): Flow<MessageEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE messageId = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: MessageStatus)

    @Query("UPDATE messages SET status = :status WHERE conversationDeviceId = :conversationDeviceId AND isOutgoing = 1 AND status != 'READ'")
    suspend fun markAllOutgoingAsStatus(conversationDeviceId: String, status: MessageStatus)

    @Query("DELETE FROM messages WHERE messageId = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("DELETE FROM messages WHERE messageId IN (:messageIds)")
    suspend fun deleteMessagesByIds(messageIds: List<String>)

    @Query("DELETE FROM messages WHERE conversationDeviceId = :conversationDeviceId")
    suspend fun deleteConversationMessages(conversationDeviceId: String)
}

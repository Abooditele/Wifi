package com.example.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.db.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM `groups` ORDER BY createdAt DESC")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM `groups` WHERE groupId = :id")
    suspend fun getGroupById(id: String): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateGroup(group: GroupEntity)

    @Query("DELETE FROM `groups` WHERE groupId = :id")
    suspend fun deleteGroup(id: String)

    @Query("UPDATE `groups` SET unreadCount = 0 WHERE groupId = :id")
    suspend fun resetUnreadCount(id: String)

    @Query("UPDATE `groups` SET unreadCount = unreadCount + 1 WHERE groupId = :id")
    suspend fun incrementUnreadCount(id: String)
}

package com.example.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.db.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY isOnline DESC, lastSeen DESC")
    fun getAllDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE deviceId = :id")
    suspend fun getDeviceById(id: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE deviceId = :id")
    fun observeDeviceById(id: String): Flow<DeviceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDevice(device: DeviceEntity)

    @Update
    suspend fun updateDevice(device: DeviceEntity)

    @Query("UPDATE devices SET isOnline = :isOnline, lastSeen = :lastSeen WHERE deviceId = :deviceId")
    suspend fun updateOnlineStatus(deviceId: String, isOnline: Boolean, lastSeen: Long = System.currentTimeMillis())

    @Query("UPDATE devices SET unreadCount = 0 WHERE deviceId = :deviceId")
    suspend fun resetUnreadCount(deviceId: String)

    @Query("UPDATE devices SET unreadCount = unreadCount + 1 WHERE deviceId = :deviceId")
    suspend fun incrementUnreadCount(deviceId: String)

    @Query("UPDATE devices SET isOnline = 0 WHERE lastSeen < :cutoffTime")
    suspend fun markOfflineDevices(cutoffTime: Long)
}

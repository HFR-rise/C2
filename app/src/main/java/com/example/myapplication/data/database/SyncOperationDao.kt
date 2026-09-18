package com.example.myapplication.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOperationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: SyncOperationEntity)

    @Query("SELECT * FROM sync_operations WHERE userId = :userId ORDER BY timestamp ASC")
    suspend fun getOperationsForUser(userId: String): List<SyncOperationEntity>

    @Query("SELECT * FROM sync_operations WHERE userId = :userId ORDER BY timestamp ASC")
    fun observeOperationsForUser(userId: String): Flow<List<SyncOperationEntity>>

    @Query("SELECT COUNT(*) FROM sync_operations WHERE userId = :userId")
    suspend fun getOperationsCountForUser(userId: String): Int

    @Query("SELECT * FROM sync_operations WHERE userId = :userId AND type = :type ORDER BY timestamp ASC")
    suspend fun getOperationsByType(userId: String, type: String): List<SyncOperationEntity>

    @Query("SELECT * FROM sync_operations WHERE userId = :userId AND entityType = :entityType ORDER BY timestamp ASC")
    suspend fun getOperationsByEntityType(userId: String, entityType: String): List<SyncOperationEntity>

    @Query("SELECT * FROM sync_operations WHERE userId = :userId AND entityId = :entityId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastOperationForEntity(userId: String, entityId: String): SyncOperationEntity?

    @Delete
    suspend fun delete(operation: SyncOperationEntity)

    @Query("DELETE FROM sync_operations WHERE id = :operationId")
    suspend fun deleteById(operationId: String)

    @Query("DELETE FROM sync_operations WHERE userId = :userId")
    suspend fun clearForUser(userId: String)

    @Query("DELETE FROM sync_operations")
    suspend fun clearAll()

    @Query("DELETE FROM sync_operations WHERE userId = :userId AND timestamp < :timestamp")
    suspend fun deleteOldOperations(userId: String, timestamp: Long)
}
package com.example.myapplication.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOperationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(operation: SyncOperationEntity)

    @Query("SELECT * FROM sync_operations WHERE userId = :userId ORDER BY updatedAt ASC")
    suspend fun getAllForUser(userId: String): List<SyncOperationEntity>

    @Query("SELECT * FROM sync_operations WHERE userId = :userId ORDER BY updatedAt ASC")
    fun observeForUser(userId: String): Flow<List<SyncOperationEntity>>

    @Query("SELECT * FROM sync_operations " +
            "WHERE userId = :userId AND entityType = :entityType " +
            "ORDER BY updatedAt ASC")
    suspend fun getForEntityType(userId: String, entityType: String): List<SyncOperationEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM sync_operations " +
            "WHERE entityType = :entityType AND entityId = :entityId)")
    suspend fun exists(entityType: String, entityId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM sync_operations " +
            "WHERE entityType = :entityType AND entityId = :entityId)")
    fun existsBlocking(entityType: String, entityId: String): Boolean

    @Query("SELECT COUNT(*) FROM sync_operations WHERE userId = :userId")
    suspend fun getCountForUser(userId: String): Int

    @Query("SELECT * FROM sync_operations " +
            "WHERE userId = :userId AND updatedAt < :threshold")
    suspend fun getOldOperations(userId: String, threshold: Long): List<SyncOperationEntity>

    @Query("SELECT * FROM sync_operations " +
            "WHERE userId = :userId AND attempts >= :maxAttempts")
    suspend fun getStuckOperations(userId: String, maxAttempts: Int): List<SyncOperationEntity>

    @Query("UPDATE sync_operations " +
            "SET attempts = attempts + 1, lastError = :error " +
            "WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun markAttempt(entityType: String, entityId: String, error: String?)

    @Query("DELETE FROM sync_operations " +
            "WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun delete(entityType: String, entityId: String)

    @Query("DELETE FROM sync_operations WHERE userId = :userId")
    suspend fun clearForUser(userId: String)

    @Query("DELETE FROM sync_operations")
    suspend fun clearAll()
}
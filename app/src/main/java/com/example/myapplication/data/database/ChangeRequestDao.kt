package com.example.myapplication.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChangeRequestDao {

    @Query("SELECT * FROM change_requests ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ChangeRequestEntity>>

    @Query("SELECT * FROM change_requests WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeForProject(projectId: String): Flow<List<ChangeRequestEntity>>

    @Query("SELECT * FROM change_requests WHERE id = :id")
    suspend fun getById(id: String): ChangeRequestEntity?

    @Query("SELECT * FROM change_requests " +
            "WHERE projectId = :projectId AND kind = 'ESTIMATE_EDIT' AND status = 'PENDING' " +
            "ORDER BY createdAt DESC LIMIT 1")
    suspend fun getPendingEstimate(projectId: String): ChangeRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ChangeRequestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ChangeRequestEntity>)

    @Query("DELETE FROM change_requests WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM change_requests WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)

    @Query("DELETE FROM change_requests")
    suspend fun deleteAll()
}
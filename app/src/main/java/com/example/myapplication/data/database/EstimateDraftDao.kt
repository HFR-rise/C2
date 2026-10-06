package com.example.myapplication.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EstimateDraftDao {

    @Query("SELECT * FROM estimate_drafts WHERE projectId = :projectId")
    suspend fun getDraft(projectId: String): EstimateDraftEntity?

    @Query("SELECT * FROM estimate_drafts WHERE projectId = :projectId")
    fun observeDraft(projectId: String): Flow<EstimateDraftEntity?>

    @Query("SELECT * FROM estimate_drafts")
    fun observeAllDrafts(): Flow<List<EstimateDraftEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: EstimateDraftEntity)

    @Query("DELETE FROM estimate_drafts WHERE projectId = :projectId")
    suspend fun delete(projectId: String)

    @Query("DELETE FROM estimate_drafts")
    suspend fun deleteAll()
}
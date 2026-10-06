package com.example.myapplication.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myapplication.data.models.Material
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDao {

    @Query("SELECT * FROM materials WHERE projectId = :projectId")
    fun getMaterialsForProject(projectId: String): Flow<List<Material>>

    @Query("SELECT * FROM materials WHERE projectId = :projectId")
    suspend fun getMaterialsForProjectOnce(projectId: String): List<Material>

    @Query("SELECT * FROM materials WHERE id = :id")
    suspend fun getMaterialById(id: String): Material?

    @Insert
    suspend fun insertMaterial(material: Material)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMaterial(material: Material)

    @Update
    suspend fun updateMaterial(material: Material)

    @Delete
    suspend fun deleteMaterial(material: Material)

    @Query("DELETE FROM materials WHERE id = :materialId")
    suspend fun deleteMaterialById(materialId: String)

    @Query("DELETE FROM materials")
    suspend fun deleteAll()

    @Query("SELECT SUM(quantity * unitPrice) FROM materials WHERE projectId = :projectId")
    suspend fun getTotalMaterialCost(projectId: String): Double?
}

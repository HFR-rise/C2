package com.example.myapplication.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myapplication.data.models.ObjectModel
import kotlinx.coroutines.flow.Flow

@Dao
interface ObjectDao {

    @Query("SELECT * FROM objects WHERE parentObjectId IS NULL ORDER BY name ASC")
    fun getRootObjects(): Flow<List<ObjectModel>>

    @Query("SELECT * FROM objects WHERE parentObjectId = :parentId ORDER BY name ASC")
    fun getChildObjects(parentId: String): Flow<List<ObjectModel>>

    @Query("SELECT * FROM objects")
    fun getAllObjects(): Flow<List<ObjectModel>>

    @Query("SELECT * FROM objects WHERE id = :id")
    suspend fun getObjectById(id: String): ObjectModel?

    @Query("SELECT * FROM objects")
    suspend fun getAllObjectsOnce(): List<ObjectModel>

    @Insert
    suspend fun insertObject(obj: ObjectModel): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertObject(obj: ObjectModel)

    @Update
    suspend fun updateObject(obj: ObjectModel)

    @Delete
    suspend fun deleteObject(obj: ObjectModel)

    @Query("DELETE FROM objects WHERE id = :objectId")
    suspend fun deleteObjectById(objectId: String)

    @Query("DELETE FROM objects WHERE parentObjectId = :parentId")
    suspend fun deleteChildObjects(parentId: String)

    @Query("DELETE FROM objects WHERE parentObjectId IS NULL")
    suspend fun deleteRootObjects()

    @Query("DELETE FROM objects")
    suspend fun deleteAll()

    @Query("UPDATE objects SET needsSync = 1 WHERE id = :objectId")
    suspend fun markAsPending(objectId: String)

    @Query("UPDATE objects SET needsSync = 0 WHERE id = :objectId")
    suspend fun markAsSynced(objectId: String)

    @Query("SELECT * FROM objects WHERE needsSync = 1")
    suspend fun getPendingObjects(): List<ObjectModel>

    @Query("SELECT COUNT(*) FROM objects WHERE needsSync = 1")
    suspend fun getPendingCount(): Int

    @Query("UPDATE objects SET needsSync = 1 WHERE id IN (:objectIds)")
    suspend fun markManyAsPending(objectIds: List<String>)
}
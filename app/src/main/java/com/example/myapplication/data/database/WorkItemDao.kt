package com.example.myapplication.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myapplication.data.models.WorkItem
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkItemDao {

    @Query("SELECT * FROM work_items WHERE projectId = :projectId ORDER BY stage ASC")
    fun getWorkItemsForProject(projectId: String): Flow<List<WorkItem>>

    @Query("SELECT * FROM work_items WHERE projectId = :projectId ORDER BY stage ASC")
    suspend fun getWorkItemsForProjectOnce(projectId: String): List<WorkItem>

    @Query("SELECT * FROM work_items WHERE id = :id")
    suspend fun getWorkItemById(id: String): WorkItem?

    @Insert
    suspend fun insertWorkItem(workItem: WorkItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkItem(workItem: WorkItem)

    @Update
    suspend fun updateWorkItem(workItem: WorkItem)

    @Delete
    suspend fun deleteWorkItem(workItem: WorkItem)

    @Query("DELETE FROM work_items WHERE id = :workItemId")
    suspend fun deleteWorkItemById(workItemId: String)

    @Query("DELETE FROM work_items")
    suspend fun deleteAll()

    @Query("SELECT SUM(laborHours * hourlyRate + materialCost) FROM work_items WHERE projectId = :projectId")
    suspend fun getTotalWorkCost(projectId: String): Double?
}
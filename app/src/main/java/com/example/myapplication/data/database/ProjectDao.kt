package com.example.myapplication.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myapplication.data.models.Project
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE objectId = :objectId")
    fun getProjectsByObjectId(objectId: String): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE userId = :userId")
    fun getProjectsForUser(userId: String): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: String): Project?

    @Query("SELECT * FROM projects")
    suspend fun getAllProjectsOnce(): List<Project>

    @Query("SELECT * FROM projects WHERE objectId = :objectId")
    suspend fun getProjectsByObjectIdOnce(objectId: String): List<Project>

    @Query("SELECT * FROM projects WHERE userId = :userId")
    suspend fun getProjectsForUserOnce(userId: String): List<Project>

    @Insert
    suspend fun insertProject(project: Project)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: Project)

    @Update
    suspend fun updateProject(project: Project)

    @Query("UPDATE projects SET objectId = :newObjectId WHERE id = :projectId")
    suspend fun updateProjectObjectId(projectId: String, newObjectId: String)

    @Query("UPDATE projects SET totalBudget = :budget, totalSpent = :spent WHERE id = :projectId")
    suspend fun updateProjectFinances(projectId: String, budget: Double, spent: Double)

    @Delete
    suspend fun deleteProject(project: Project)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: String)

    @Query("DELETE FROM projects")
    suspend fun deleteAll()
}
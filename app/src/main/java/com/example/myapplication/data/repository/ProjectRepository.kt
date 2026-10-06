package com.example.myapplication.data.repository

import com.example.myapplication.data.database.MaterialDao
import com.example.myapplication.data.database.ProjectDao
import com.example.myapplication.data.database.WorkItemDao
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.Project
import com.example.myapplication.data.models.WorkItem
import com.example.myapplication.network.ApiService
import kotlinx.coroutines.flow.Flow
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val materialDao: MaterialDao,
    private val workItemDao: WorkItemDao,
    private val apiService: ApiService
) {

    private companion object {
        const val TAG = "ProjectRepository"
    }

    fun getAllProjects(): Flow<List<Project>> = projectDao.getAllProjects()

    fun getProjectsByObjectId(objectId: String): Flow<List<Project>> =
        projectDao.getProjectsByObjectId(objectId)

    fun getProjectsForUser(userId: String): Flow<List<Project>> =
        projectDao.getProjectsForUser(userId)

    fun getMaterials(projectId: String): Flow<List<Material>> =
        materialDao.getMaterialsForProject(projectId)

    fun getWorkItems(projectId: String): Flow<List<WorkItem>> =
        workItemDao.getWorkItemsForProject(projectId)

    suspend fun getProjectById(projectId: String): Project? =
        projectDao.getProjectById(projectId)

    suspend fun getProjectsByObjectIdOnce(objectId: String): List<Project> =
        projectDao.getProjectsByObjectIdOnce(objectId)

    suspend fun getProjectsForUserOnce(userId: String): List<Project> =
        projectDao.getProjectsForUserOnce(userId)

    suspend fun getMaterialsOnce(projectId: String): List<Material> =
        materialDao.getMaterialsForProjectOnce(projectId)

    suspend fun getWorkItemsOnce(projectId: String): List<WorkItem> =
        workItemDao.getWorkItemsForProjectOnce(projectId)

    suspend fun createProject(project: Project): Project {
        val withNeedsSync = project.copy(needsSync = true)
        projectDao.insertProject(withNeedsSync)
        return withNeedsSync
    }

    suspend fun updateProject(project: Project) {
        projectDao.updateProject(project.copy(needsSync = true))
    }

    suspend fun deleteProject(project: Project) =
        projectDao.deleteProject(project)

    suspend fun updateProjectObjectId(projectId: String, newObjectId: String) {
        projectDao.updateProjectObjectId(projectId, newObjectId)
        projectDao.markAsPending(projectId)
    }

    suspend fun addMaterial(material: Material) {
        materialDao.insertMaterial(material)
        updateProjectTotal(material.projectId)
        projectDao.markAsPending(material.projectId)
    }

    suspend fun updateMaterial(material: Material) {
        materialDao.updateMaterial(material)
        updateProjectTotal(material.projectId)
        projectDao.markAsPending(material.projectId)
    }

    suspend fun deleteMaterial(material: Material) {
        materialDao.deleteMaterial(material)
        updateProjectTotal(material.projectId)
        projectDao.markAsPending(material.projectId)
    }

    suspend fun addWorkItem(workItem: WorkItem) {
        workItemDao.insertWorkItem(workItem)
        updateProjectTotal(workItem.projectId)
        projectDao.markAsPending(workItem.projectId)
    }

    suspend fun updateWorkItem(workItem: WorkItem) {
        workItemDao.updateWorkItem(workItem)
        updateProjectTotal(workItem.projectId)
        projectDao.markAsPending(workItem.projectId)
    }

    suspend fun deleteWorkItem(workItem: WorkItem) {
        workItemDao.deleteWorkItem(workItem)
        updateProjectTotal(workItem.projectId)
        projectDao.markAsPending(workItem.projectId)
    }

    suspend fun markAsPending(projectId: String) {
        projectDao.markAsPending(projectId)
    }

    suspend fun markAsSynced(projectId: String) {
        projectDao.markAsSynced(projectId)
    }

    suspend fun getPendingProjects(): List<Project> =
        projectDao.getPendingProjects()

    suspend fun deleteAllProjects() {
        projectDao.deleteAll()
    }

    private suspend fun updateProjectTotal(projectId: String) {
        val totalMaterial = materialDao.getTotalMaterialCost(projectId) ?: 0.0
        val totalWork = workItemDao.getTotalWorkCost(projectId) ?: 0.0
        projectDao.updateProjectFinances(projectId, totalMaterial + totalWork, 0.0)
    }
}
package com.example.myapplication.data.repository

import com.example.myapplication.data.database.EstimateDraftDao
import com.example.myapplication.data.database.EstimateDraftEntity
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.WorkItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EstimateDraftRepository @Inject constructor(
    private val draftDao: EstimateDraftDao,
    private val gson: Gson
) {
    private val materialsType = object : TypeToken<List<Material>>() {}.type
    private val workItemsType = object : TypeToken<List<WorkItem>>() {}.type

    suspend fun getDraft(projectId: String): DraftData? {
        val entity = draftDao.getDraft(projectId) ?: return null
        return entity.toDraftData()
    }

    fun observeDraft(projectId: String) = draftDao.observeDraft(projectId)

    fun observeAllDrafts() = draftDao.observeAllDrafts()

    suspend fun saveDraft(data: DraftData) {
        draftDao.upsert(data.toEntity())
    }

    suspend fun deleteDraft(projectId: String) = draftDao.delete(projectId)

    suspend fun deleteAll() = draftDao.deleteAll()

    private fun EstimateDraftEntity.toDraftData(): DraftData {
        val materials: List<Material> = gson.fromJson(materialsJson, materialsType)
        val workItems: List<WorkItem> = gson.fromJson(workItemsJson, workItemsType)
        return DraftData(
            projectId = projectId,
            projectName = projectName,
            projectDescription = projectDescription,
            materials = materials,
            workItems = workItems,
            comment = comment,
            serverChangeId = serverChangeId,
            serverStatus = serverStatus
        )
    }

    private fun DraftData.toEntity(): EstimateDraftEntity = EstimateDraftEntity(
        projectId = projectId,
        projectName = projectName,
        projectDescription = projectDescription,
        materialsJson = gson.toJson(materials),
        workItemsJson = gson.toJson(workItems),
        comment = comment,
        serverChangeId = serverChangeId,
        serverStatus = serverStatus
    )
}

data class DraftData(
    val projectId: String,
    val projectName: String,
    val projectDescription: String,
    val materials: List<Material>,
    val workItems: List<WorkItem>,
    val comment: String? = null,
    val serverChangeId: String? = null,
    val serverStatus: String? = null
)
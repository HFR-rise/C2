package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.database.ObjectDao
import com.example.myapplication.data.database.ProjectDao
import com.example.myapplication.data.models.ObjectModel
import com.example.myapplication.data.models.Project
import com.example.myapplication.utils.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObjectRepository @Inject constructor(
    private val objectDao: ObjectDao,
    private val projectDao: ProjectDao,
    private val userPreferences: UserPreferences
) {

    private companion object {
        const val TAG = "ObjectRepository"
        const val ROOT_OBJECT_NAME = "Без объекта"
    }

    fun getRootObjects(): Flow<List<ObjectModel>> = objectDao.getRootObjects()

    fun getChildObjects(parentId: String): Flow<List<ObjectModel>> =
        objectDao.getChildObjects(parentId)

    fun getAllObjects(): Flow<List<ObjectModel>> = objectDao.getAllObjects()

    fun getProjectsForObject(objectId: String): Flow<List<Project>> =
        projectDao.getProjectsByObjectId(objectId)

    suspend fun getObjectById(id: String): ObjectModel? = objectDao.getObjectById(id)

    suspend fun getRootObjectsOnce(): List<ObjectModel> = objectDao.getRootObjects().first()

    suspend fun getChildObjectsOnce(parentId: String): List<ObjectModel> =
        objectDao.getChildObjects(parentId).first()

    suspend fun getAllObjectsOnce(): List<ObjectModel> =
        objectDao.getAllObjectsOnce()

    suspend fun insertObject(objectModel: ObjectModel): Long =
        objectDao.insertObject(objectModel)

    suspend fun updateObject(objectModel: ObjectModel) =
        objectDao.updateObject(objectModel)

    suspend fun upsertObject(objectModel: ObjectModel) =
        objectDao.upsertObject(objectModel)

    suspend fun deleteObjectWithCascade(objectModel: ObjectModel): DeletionResult {
        val allChildObjects = getAllChildObjectsRecursive(objectModel.id)
        val allObjectIds = listOf(objectModel.id) + allChildObjects.map { it.id }

        var projectsDeleted = 0
        for (objId in allObjectIds) {
            val projects = projectDao.getProjectsByObjectIdOnce(objId)
            projects.forEach {
                projectDao.deleteProject(it)
                projectsDeleted++
            }
        }

        objectDao.deleteObject(objectModel)

        Log.d(TAG, "Cascade delete: ${allObjectIds.size} objects, $projectsDeleted projects")

        return DeletionResult(
            objectsDeleted = allObjectIds.size,
            projectsDeleted = projectsDeleted,
            materialsDeleted = 0,
            workItemsDeleted = 0
        )
    }

    suspend fun deleteObjectSimple(objectModel: ObjectModel) =
        objectDao.deleteObject(objectModel)

    suspend fun deleteProjectWithAllData(project: Project): ProjectDeletionResult {
        projectDao.deleteProject(project)
        Log.d(TAG, "Project deleted: ${project.name}")
        return ProjectDeletionResult(
            projectsDeleted = 1,
            materialsDeleted = 0,
            workItemsDeleted = 0
        )
    }

    suspend fun cleanupOrphanedProjects(): Int {
        val rootObjectId = getOrCreateRootObjectId()
        val allObjects = getAllObjectsOnce()
        val validObjectIds = allObjects.map { it.id }.toSet()

        val allProjects = projectDao.getAllProjectsOnce()
        val orphanedProjects = allProjects.filter { project ->
            project.objectId != null &&
                    project.objectId !in validObjectIds &&
                    project.objectId != rootObjectId
        }

        orphanedProjects.forEach { project ->
            projectDao.updateProject(project.copy(objectId = rootObjectId))
        }

        if (orphanedProjects.isNotEmpty()) {
            Log.d(TAG, "Fixed ${orphanedProjects.size} orphaned projects")
        }

        return orphanedProjects.size
    }

    suspend fun getTotalObjectsCount(): Int =
        objectDao.getAllObjectsOnce().size

    suspend fun getTotalProjectsCount(): Int =
        projectDao.getAllProjectsOnce().size

    suspend fun getOrCreateRootObjectId(): String {
        val existingRoot = getRootObjectsOnce().find { it.name == ROOT_OBJECT_NAME }
        if (existingRoot != null) return existingRoot.id

        val newRoot = ObjectModel(
            name = ROOT_OBJECT_NAME,
            description = "Корневые сметы (автоматически создан)",
            userId = userPreferences.getUserId().orEmpty()
        )
        objectDao.insertObject(newRoot)
        Log.d(TAG, "Created root object: ${newRoot.id}")
        return newRoot.id
    }

    suspend fun getRootObjectId(): String? =
        getRootObjectsOnce().find { it.name == ROOT_OBJECT_NAME }?.id

    suspend fun deleteAllObjects() {
        objectDao.deleteAll()
    }

    private suspend fun getAllChildObjectsRecursive(parentId: String): List<ObjectModel> {
        val allChildren = mutableListOf<ObjectModel>()
        val directChildren = objectDao.getChildObjects(parentId).first()

        for (child in directChildren) {
            allChildren.add(child)
            allChildren.addAll(getAllChildObjectsRecursive(child.id))
        }
        return allChildren
    }
}

data class DeletionResult(
    val objectsDeleted: Int,
    val projectsDeleted: Int,
    val materialsDeleted: Int,
    val workItemsDeleted: Int
) {
    fun getTotalItemsDeleted(): Int =
        objectsDeleted + projectsDeleted + materialsDeleted + workItemsDeleted

    override fun toString(): String =
        "Удалено: объектов: $objectsDeleted, смет: $projectsDeleted, " +
                "материалов: $materialsDeleted, работ: $workItemsDeleted"
}

data class ProjectDeletionResult(
    val projectsDeleted: Int,
    val materialsDeleted: Int,
    val workItemsDeleted: Int
)
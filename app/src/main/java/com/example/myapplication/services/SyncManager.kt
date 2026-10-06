package com.example.myapplication.services

import android.content.Context
import android.util.Log
import com.example.myapplication.data.database.ChangeRequestDao
import com.example.myapplication.data.database.ChangeRequestEntity
import com.example.myapplication.data.database.ContactDao
import com.example.myapplication.data.database.ContactMethodDao
import com.example.myapplication.data.database.MaterialDao
import com.example.myapplication.data.database.ObjectDao
import com.example.myapplication.data.database.ProjectDao
import com.example.myapplication.data.database.SyncOperationDao
import com.example.myapplication.data.database.SyncOperationEntity
import com.example.myapplication.data.database.WorkItemDao
import com.example.myapplication.data.models.ChangeRequestDto
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.data.models.ContactMethodSnapshotDto
import com.example.myapplication.data.models.ContactSnapshotDto
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.MaterialSnapshotDto
import com.example.myapplication.data.models.ObjectModel
import com.example.myapplication.data.models.ObjectSnapshotDto
import com.example.myapplication.data.models.Project
import com.example.myapplication.data.models.ProjectCreateRequest
import com.example.myapplication.data.models.ProjectDto
import com.example.myapplication.data.models.ProjectSnapshotDto
import com.example.myapplication.data.models.ProjectUpdateRequest
import com.example.myapplication.data.models.WorkItem
import com.example.myapplication.data.models.WorkItemSnapshotDto
import com.example.myapplication.network.ApiService
import com.example.myapplication.utils.NetworkUtils
import com.example.myapplication.utils.UserPreferences
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val apiService: ApiService,
    private val projectDao: ProjectDao,
    private val materialDao: MaterialDao,
    private val workItemDao: WorkItemDao,
    private val contactDao: ContactDao,
    private val contactMethodDao: ContactMethodDao,
    private val objectDao: ObjectDao,
    private val syncOperationDao: SyncOperationDao,
    private val changeRequestDao: ChangeRequestDao,
    private val userPreferences: UserPreferences,
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val networkUtils: NetworkUtils,
    private val webSocketService: WebSocketService
) {
    private companion object {
        const val TAG = "SyncManager"
        const val PERIODIC_SYNC_INTERVAL_MS = 60_000L
        const val INITIAL_RETRY_DELAY_MS = 2_000L
        const val MAX_RETRY_DELAY_MS = 30_000L
        const val MAX_RETRY_ATTEMPTS = 8
        const val STUCK_OPERATION_TTL_MS = 30 * 60 * 1000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isSyncing = AtomicBoolean(false)
    private var periodicSyncJob: Job? = null

    var onForceLogout: (() -> Unit)? = null

    fun startPeriodicSync() {
        if (periodicSyncJob?.isActive == true) return

        webSocketService.onProjectChanged = { projectId ->
            scope.launch {
                runCatching { loadSingleProject(projectId) }
                    .onFailure { Log.e(TAG, "onProjectChanged reload failed: ${it.message}", it) }
            }
        }

        periodicSyncJob = scope.launch {
            while (true) {
                val userId = userPreferences.getUserId()
                if (userId != null && networkUtils.isOnline(context)) {
                    runCatching { syncIfQueueIsEmpty(userId) }
                        .onFailure { Log.e(TAG, "Periodic sync error: ${it.message}") }
                }
                delay(PERIODIC_SYNC_INTERVAL_MS)
            }
        }
        Log.d(TAG, "Periodic sync started")
    }

    fun stopPeriodicSync() {
        periodicSyncJob?.cancel()
        periodicSyncJob = null
        webSocketService.onProjectChanged = null
        Log.d(TAG, "Periodic sync stopped")
    }

    suspend fun syncIfQueueIsEmpty(userId: String) {
        if (!isSyncing.compareAndSet(false, true)) {
            Log.d(TAG, "Sync already in progress, skipping")
            return
        }
        try {
            Log.d(TAG, "syncIfQueueIsEmpty: START userId=$userId")
            syncPendingProjects()
            syncPendingContacts()
            syncPendingObjects()

            val queueSize = syncOperationDao.getCountForUser(userId)
            Log.d(TAG, "syncIfQueueIsEmpty: queue size=$queueSize")

            if (queueSize > 0) {
                syncPendingOperationsToServer(userId, retryUntilSuccess = true)
            }
            syncDataFromServer(userId, skipQueueCheck = true)
            Log.d(TAG, "syncIfQueueIsEmpty: DONE")
        } finally {
            isSyncing.set(false)
        }
    }

    suspend fun handleOnlineRecovery() {
        if (!isSyncing.compareAndSet(false, true)) return
        try {
            Log.d(TAG, "handleOnlineRecovery: START")
            val userId = userPreferences.getUserId() ?: return
            syncPendingProjects()
            syncPendingContacts()
            syncPendingObjects()
            syncPendingOperationsToServer(userId, retryUntilSuccess = true)
            syncDataFromServer(userId, skipQueueCheck = true)
            Log.d(TAG, "handleOnlineRecovery: DONE")
        } finally {
            isSyncing.set(false)
        }
    }

    suspend fun checkCurrentSession(): Boolean {
        val deviceId = userPreferences.getDeviceId() ?: return false
        if (!networkUtils.isOnline(context)) return true

        return runCatching {
            val response = apiService.checkSessionWithDevice(deviceId)
            response.isSuccessful && response.body()?.isValid == true
        }.getOrElse { e ->
            when (e) {
                is ConnectException, is SocketTimeoutException -> true
                else -> {
                    Log.e(TAG, "Session check failed: ${e.message}")
                    false
                }
            }
        }
    }

    suspend fun syncDataFromServer(userId: String, skipQueueCheck: Boolean = false) {
        if (!networkUtils.isOnline(context)) return

        coroutineScope {
            launch { loadProjects(userId) }
            launch { loadObjects() }
            launch { loadContacts() }
            launch { loadContactMethods() }
            launch { loadChanges() }

            if (!skipQueueCheck) {
                launch {
                    runCatching { syncPendingOperationsToServer(userId, retryUntilSuccess = false) }
                        .onFailure { Log.e(TAG, "Queue sync failed: ${it.message}") }
                }
            }
        }
    }

    suspend fun syncProjectNow(projectId: String): Boolean {
        if (!networkUtils.isOnline(context)) return false

        val project = projectDao.getProjectById(projectId) ?: return false
        if (!project.needsSync) return true

        return try {
            val materials = materialDao.getMaterialsForProjectOnce(projectId)
            val workItems = workItemDao.getWorkItemsForProjectOnce(projectId)

            val snapshot = ProjectSnapshotDto(
                name = project.name,
                description = project.description.ifBlank { null },
                objectId = project.objectId ?: "",
                materials = materials.map { it.toSnapshotDto() },
                workItems = workItems.map { it.toSnapshotDto() },
                totalBudget = project.totalBudget,
                comment = null
            )

            val response = apiService.syncProject(projectId, snapshot)

            if (response.isSuccessful) {
                projectDao.markAsSynced(projectId)
                response.body()?.let { dto ->
                    runCatching { upsertEntity(dto.toEntity()) }
                }
                true
            } else if (response.code() in 400..499) {
                projectDao.markAsSynced(projectId)
                false
            } else {
                false
            }
        } catch (e: ConnectException) {
            false
        } catch (e: SocketTimeoutException) {
            false
        } catch (e: Exception) {
            Log.e(TAG, "syncProjectNow($projectId) failed: ${e.message}", e)
            false
        }
    }

    private suspend fun <T> syncPending(
        entityName: String,
        getPending: suspend () -> List<T>,
        syncOne: suspend (T) -> Boolean,
        markSynced: suspend (T) -> Unit
    ) {
        val pending = getPending()
        if (pending.isEmpty()) return

        Log.d(TAG, "Syncing ${pending.size} pending $entityName via snapshot")

        for (item in pending) {
            if (userPreferences.getUserId() == null) return
            try {
                if (syncOne(item)) markSynced(item)
            } catch (e: ConnectException) {
                return
            } catch (e: SocketTimeoutException) {
                return
            } catch (e: Exception) {
                Log.e(TAG, "sync $entityName error: ${e.message}", e)
            }
        }
    }

    private suspend fun syncPendingProjects() = syncPending(
        entityName = "projects",
        getPending = { projectDao.getPendingProjects() },
        syncOne = { project -> syncProjectSnapshot(project) },
        markSynced = { project -> projectDao.markAsSynced(project.id) }
    )

    private suspend fun syncPendingContacts() = syncPending(
        entityName = "contacts",
        getPending = { contactDao.getPendingContacts() },
        syncOne = { contact -> syncContactSnapshot(contact) },
        markSynced = { contact -> contactDao.markAsSynced(contact.id) }
    )

    private suspend fun syncPendingObjects() = syncPending(
        entityName = "objects",
        getPending = { objectDao.getPendingObjects() },
        syncOne = { obj -> syncObjectSnapshot(obj) },
        markSynced = { obj -> objectDao.markAsSynced(obj.id) }
    )

    private suspend fun syncProjectSnapshot(project: Project): Boolean {
        val materials = materialDao.getMaterialsForProjectOnce(project.id)
        val workItems = workItemDao.getWorkItemsForProjectOnce(project.id)

        val snapshot = ProjectSnapshotDto(
            name = project.name,
            description = project.description.ifBlank { null },
            objectId = project.objectId ?: "",
            materials = materials.map { it.toSnapshotDto() },
            workItems = workItems.map { it.toSnapshotDto() },
            totalBudget = project.totalBudget,
            comment = null
        )

        val response = apiService.syncProject(project.id, snapshot)

        if (response.isSuccessful) {
            response.body()?.let { dto ->
                runCatching { upsertEntity(dto.toEntity()) }
            }
            return true
        }
        return handleSyncFailure("syncProject", project.id, response)
    }

    private suspend fun syncContactSnapshot(contact: Contact): Boolean {
        val methods = contactMethodDao.getContactMethodsOnce(contact.id)

        val snapshot = ContactSnapshotDto(
            name = contact.name,
            description = contact.description,
            methods = methods.map {
                ContactMethodSnapshotDto(
                    id = it.id,
                    methodType = it.methodType,
                    value = it.value
                )
            }
        )

        val response = apiService.syncContact(contact.id, snapshot)

        if (response.isSuccessful) {
            response.body()?.let { dto ->
                runCatching { contactDao.upsertContact(dto) }
            }
            return true
        }
        return handleSyncFailure("syncContact", contact.id, response)
    }

    private suspend fun syncObjectSnapshot(obj: ObjectModel): Boolean {
        val snapshot = ObjectSnapshotDto(
            name = obj.name,
            street = obj.street.ifBlank { null },
            house = obj.house.ifBlank { null },
            building = obj.building.ifBlank { null },
            description = obj.description.ifBlank { null },
            parentObjectId = obj.parentObjectId ?: ""
        )

        val response = apiService.syncObject(obj.id, snapshot)

        if (response.isSuccessful) {
            response.body()?.let { dto ->
                runCatching { objectDao.upsertObject(dto) }
            }
            return true
        }
        return handleSyncFailure("syncObject", obj.id, response)
    }

    private fun handleSyncFailure(tag: String, entityId: String, response: Response<*>): Boolean {
        return if (response.code() in 400..499) {
            Log.w(TAG, "$tag $entityId: ${response.code()} — dropping")
            true
        } else {
            Log.w(TAG, "$tag $entityId: ${response.code()} — will retry")
            false
        }
    }

    suspend fun reloadProject(projectId: String) = loadSingleProject(projectId)

    suspend fun reloadProjectForce(projectId: String) {
        if (!networkUtils.isOnline(context)) {
            Log.d(TAG, "reloadProjectForce($projectId): no network")
            return
        }

        runCatching {
            val response = apiService.getProject(projectId)
            if (!response.isSuccessful) {
                Log.w(TAG, "reloadProjectForce($projectId): code=${response.code()}")
                return@runCatching
            }

            response.body()?.let { dto ->
                Log.d(TAG, "reloadProjectForce($projectId): applying server version=${dto.version}")

                val entity = dto.toEntity().copy(
                    userId = userPreferences.getUserId().orEmpty(),
                    needsSync = false
                )
                val local = projectDao.getProjectById(projectId)
                if (local == null) projectDao.insertProject(entity)
                else projectDao.updateProject(entity)

                dto.materials?.forEach { upsertEntity(it) }
                dto.workItems?.forEach { upsertEntity(it) }
            }
        }.onFailure { Log.e(TAG, "reloadProjectForce($projectId) failed: ${it.message}", it) }
    }

    fun reloadProjectAsync(projectId: String) {
        scope.launch {
            runCatching { loadSingleProject(projectId) }
                .onFailure { Log.e(TAG, "reloadProjectAsync($projectId) failed: ${it.message}", it) }
        }
    }

    private suspend fun loadSingleProject(projectId: String) {
        if (!networkUtils.isOnline(context)) return

        runCatching {
            val response = apiService.getProject(projectId)
            if (!response.isSuccessful) return@runCatching

            response.body()?.let { dto ->
                val local = projectDao.getProjectById(projectId)
                val shouldApply = local == null
                        || local.needsSync != true
                        || (dto.version ?: 0L) > (local.version ?: 0L)

                if (shouldApply) {
                    upsertEntity(dto.toEntity())
                    dto.materials?.forEach { upsertEntity(it) }
                    dto.workItems?.forEach { upsertEntity(it) }
                } else {
                    Log.d(TAG, "loadSingleProject($projectId): skip — local needsSync, version=${local?.version}")
                }
            }
        }.onFailure { Log.e(TAG, "loadSingleProject($projectId) failed: ${it.message}", it) }
    }

    suspend fun queueOperation(type: String, entityType: String, entityId: String, data: Any) {
        val userId = userPreferences.getUserId()
        if (userId == null) {
            Log.w(TAG, "queueOperation: no userId — SKIPPED ($type $entityType $entityId)")
            return
        }
        Log.d(TAG, "queueOperation: ENQUEUE $type $entityType $entityId (userId=$userId)")
        syncOperationDao.enqueue(
            SyncOperationEntity(
                entityType = entityType,
                entityId = entityId,
                operation = type,
                payload = gson.toJson(data),
                updatedAt = System.currentTimeMillis(),
                userId = userId
            )
        )
        Log.d(TAG, "queueOperation: enqueued OK ($type $entityType $entityId)")
    }

    private fun Project.toUpdateRequest() = ProjectUpdateRequest(
        id = id,
        name = name,
        description = description.ifBlank { null },
        objectId = objectId?.takeIf { it.isNotBlank() }
    )

    suspend fun syncEntityToServer(entity: Any): Boolean {
        val type = getEntityType(entity)
        val id = getEntityId(entity)

        try {
            val response = when (entity) {
                is Project -> {
                    if (projectExistsOnServer(entity.id) || isExistingProject(entity)) {
                        apiService.updateProject(entity.id, entity.toUpdateRequest())
                    } else {
                        apiService.createProject(
                            ProjectCreateRequest(
                                id = entity.id,
                                name = entity.name,
                                description = entity.description,
                                objectId = entity.objectId
                            )
                        )
                    }
                }
                is ObjectModel -> apiService.createObject(entity)
                is Contact -> apiService.createContact(entity)
                is Material -> apiService.addMaterial(entity)
                is WorkItem -> apiService.addWorkItem(entity)
                is ContactMethod -> apiService.addContactMethod(entity)
                else -> return false
            }

            if (response.isSuccessful) {
                response.body()?.let { runCatching { upsertEntity(it) } }
                return true
            }
            val opType = if (type == "PROJECT") "UPDATE" else "CREATE"
            queueOperation(opType, type, id, entity)
            return false
        } catch (e: Exception) {
            val opType = if (type == "PROJECT") "UPDATE" else "CREATE"
            queueOperation(opType, type, id, entity)
            return false
        }
    }

    private suspend fun projectExistsOnServer(projectId: String): Boolean {
        if (projectId.isBlank()) return false
        return runCatching { apiService.getProject(projectId).isSuccessful }.getOrDefault(false)
    }

    private fun isExistingProject(project: Project): Boolean = project.version != null

    suspend fun syncProjectToServer(project: Project): Boolean = syncEntityToServer(project)
    suspend fun syncObjectToServer(obj: ObjectModel): Boolean = syncEntityToServer(obj)
    suspend fun syncContactToServer(contact: Contact): Boolean = syncEntityToServer(contact)

    suspend fun syncProjectDeletion(projectId: String) {
        try {
            val response = apiService.deleteProject(projectId)
            if (!response.isSuccessful && response.code() in 500..599) {
                queueOperation("DELETE", "PROJECT", projectId, mapOf("id" to projectId))
            }
        } catch (e: Exception) {
            queueOperation("DELETE", "PROJECT", projectId, mapOf("id" to projectId))
        }
    }

    suspend fun syncObjectDeletion(objectId: String) {
        if (objectId.isBlank()) return

        if (!networkUtils.isOnline(context)) {
            Log.d(TAG, "syncObjectDeletion($objectId): no network — queued")
            queueOperation("DELETE", "OBJECT", objectId, mapOf("id" to objectId))
            return
        }

        try {
            Log.d(TAG, "syncObjectDeletion: DELETE /api/objects/$objectId")
            val response = apiService.deleteObject(objectId)
            Log.d(TAG, "syncObjectDeletion($objectId): response code=${response.code()}")

            when {
                response.isSuccessful -> Log.d(TAG, "syncObjectDeletion($objectId): OK")
                response.code() in 400..499 -> {
                    val errBody = try { response.errorBody()?.string() } catch (e: Exception) { null }
                    Log.w(TAG, "syncObjectDeletion($objectId): ${response.code()} — dropping, body=$errBody")
                }
                else -> {
                    Log.w(TAG, "syncObjectDeletion($objectId): ${response.code()} — queued")
                    queueOperation("DELETE", "OBJECT", objectId, mapOf("id" to objectId))
                }
            }
        } catch (e: ConnectException) {
            queueOperation("DELETE", "OBJECT", objectId, mapOf("id" to objectId))
        } catch (e: SocketTimeoutException) {
            queueOperation("DELETE", "OBJECT", objectId, mapOf("id" to objectId))
        } catch (e: Exception) {
            Log.e(TAG, "syncObjectDeletion($objectId) failed: ${e.message}", e)
            queueOperation("DELETE", "OBJECT", objectId, mapOf("id" to objectId))
        }
    }

    suspend fun clearAllLocalData() {
        projectDao.deleteAll()
        objectDao.deleteAll()
        contactDao.deleteAll()
        contactMethodDao.deleteAll()
        materialDao.deleteAll()
        workItemDao.deleteAll()
        changeRequestDao.deleteAll()
        syncOperationDao.clearAll()
    }

    suspend fun clearAllOperations() = syncOperationDao.clearAll()
    suspend fun clearAllOperationsForUser(userId: String) = syncOperationDao.clearForUser(userId)

    fun hasInternetConnection(): Boolean = networkUtils.isOnline(context)
    fun currentUserId(): String? = userPreferences.getUserId()

    suspend fun getPendingOperationsCount(): Int =
        userPreferences.getUserId()?.let { syncOperationDao.getCountForUser(it) } ?: 0

    suspend fun getPendingProjectsCount(): Int = projectDao.getPendingCount()
    suspend fun getPendingContactsCount(): Int = contactDao.getPendingCount()
    suspend fun getPendingObjectsCount(): Int = objectDao.getPendingCount()

    private suspend fun syncPendingOperationsToServer(
        userId: String,
        retryUntilSuccess: Boolean = false
    ): Boolean {
        var attempt = 0
        var delayMs = INITIAL_RETRY_DELAY_MS

        val stuckThreshold = System.currentTimeMillis() - STUCK_OPERATION_TTL_MS
        val stuck = syncOperationDao.getOldOperations(userId, stuckThreshold)
        if (stuck.isNotEmpty()) {
            Log.w(TAG, "syncPendingOperations: dropping ${stuck.size} stuck operations")
            stuck.forEach { syncOperationDao.delete(it.entityType, it.entityId) }
        }

        while (true) {
            attempt++
            if (userPreferences.getUserId() != userId) return false

            val operations = syncOperationDao.getAllForUser(userId)
            Log.d(TAG, "syncPendingOperations: attempt=$attempt, size=${operations.size}")

            if (operations.isEmpty()) return true

            val successOps = mutableListOf<SyncOperationEntity>()
            var hasFailures = false
            for (op in operations) {
                if (dispatchOperation(op)) successOps.add(op) else hasFailures = true
            }
            successOps.forEach { syncOperationDao.delete(it.entityType, it.entityId) }

            if (!hasFailures) return true
            if (!retryUntilSuccess) return false
            if (!networkUtils.isOnline(context)) return false
            if (attempt >= MAX_RETRY_ATTEMPTS) return false

            delay(delayMs)
            delayMs = (delayMs * 1.5).coerceAtMost(MAX_RETRY_DELAY_MS.toDouble()).toLong()
        }
    }

    private suspend fun dispatchOperation(op: SyncOperationEntity): Boolean {
        return try {
            Log.d(TAG, "dispatchOperation: ${op.operation} ${op.entityType} ${op.entityId}")

            val response = when (op.operation) {
                "DELETE" -> dispatchDelete(op)
                "UPDATE" -> dispatchUpdate(op)
                "CREATE" -> dispatchCreate(op)
                else -> {
                    Log.w(TAG, "dispatchOperation: unknown op=${op.operation} — dropping")
                    return true
                }
            } ?: run {
                Log.w(TAG, "dispatchOperation: null response for ${op.operation} — dropping")
                return true
            }

            when {
                response.isSuccessful -> {
                    response.body()?.let { runCatching { upsertEntity(it) } }
                    true
                }
                response.code() == 404 -> true
                response.code() in 400..499 -> {
                    val errBody = try { response.errorBody()?.string() } catch (e: Exception) { null }
                    Log.w(TAG, "dispatchOperation: 4xx (${response.code()}) — dropping, body=$errBody")
                    true
                }
                response.code() == 500 && op.operation == "CREATE" &&
                        isDuplicateIdentifierError(
                            runCatching { response.errorBody()?.string() }.getOrNull()
                        ) -> {
                    if (op.entityType == "PROJECT") refreshProjectAfterDuplicate(op.entityId)
                    true
                }
                else -> false
            }
        } catch (e: ConnectException) {
            false
        } catch (e: SocketTimeoutException) {
            false
        } catch (e: Exception) {
            Log.e(TAG, "dispatchOperation error: ${e.message}", e)
            false
        }
    }

    private suspend fun dispatchDelete(op: SyncOperationEntity): Response<*>? {
        return when (op.entityType) {
            "PROJECT" -> apiService.deleteProject(op.entityId)
            "OBJECT" -> apiService.deleteObject(op.entityId)
            "CONTACT" -> apiService.deleteContact(op.entityId)
            "MATERIAL" -> apiService.deleteMaterial(op.entityId)
            "WORK_ITEM" -> apiService.deleteWorkItem(op.entityId)
            else -> null
        }
    }

    private suspend fun dispatchUpdate(op: SyncOperationEntity): Response<*>? {
        val payload = op.payload ?: return null
        return when (op.entityType) {
            "PROJECT" -> apiService.updateProject(
                op.entityId,
                gson.fromJson(payload, Project::class.java).toUpdateRequest()
            )
            "OBJECT" -> apiService.updateObject(op.entityId, gson.fromJson(payload, ObjectModel::class.java))
            "CONTACT" -> apiService.updateContact(op.entityId, gson.fromJson(payload, Contact::class.java))
            else -> null
        }
    }

    private suspend fun dispatchCreate(op: SyncOperationEntity): Response<*>? {
        val payload = op.payload ?: return null
        return when (op.entityType) {
            "PROJECT" -> {
                val project = gson.fromJson(payload, Project::class.java)
                if (projectExistsOnServer(project.id) || isExistingProject(project)) {
                    apiService.updateProject(project.id, project.toUpdateRequest())
                } else {
                    apiService.createProject(
                        ProjectCreateRequest(
                            id = project.id,
                            name = project.name,
                            description = project.description,
                            objectId = project.objectId
                        )
                    )
                }
            }
            "OBJECT" -> apiService.createObject(gson.fromJson(payload, ObjectModel::class.java))
            "CONTACT" -> apiService.createContact(gson.fromJson(payload, Contact::class.java))
            "MATERIAL" -> apiService.addMaterial(gson.fromJson(payload, Material::class.java))
            "WORK_ITEM" -> apiService.addWorkItem(gson.fromJson(payload, WorkItem::class.java))
            "CONTACT_METHOD" -> apiService.addContactMethod(gson.fromJson(payload, ContactMethod::class.java))
            else -> null
        }
    }

    private fun isDuplicateIdentifierError(errorBody: String?): Boolean =
        !errorBody.isNullOrBlank() &&
                errorBody.contains("A different object with the same identifier value", ignoreCase = true)

    private suspend fun refreshProjectAfterDuplicate(projectId: String) {
        if (projectId.isBlank()) return
        runCatching {
            val response = apiService.getProject(projectId)
            if (response.isSuccessful) {
                response.body()?.let { runCatching { upsertEntity(it.toEntity()) } }
            }
        }
    }

    private suspend fun upsertEntity(entity: Any) {
        when (entity) {
            is ProjectDto -> {
                if (projectDao.getProjectById(entity.id)?.needsSync == true) return
                upsertEntity(entity.toEntity().copy(
                    userId = userPreferences.getUserId().orEmpty(),
                    needsSync = false
                ))
                entity.materials?.forEach { upsertEntity(it) }
                entity.workItems?.forEach { upsertEntity(it) }
            }
            is Project -> {
                val local = projectDao.getProjectById(entity.id)
                if (local?.needsSync == true && !entity.needsSync) return
                if (local == null) projectDao.insertProject(entity)
                else projectDao.updateProject(entity)
            }
            is ObjectModel -> {
                val local = objectDao.getObjectById(entity.id)
                if (local?.needsSync == true) return
                if (local == null) objectDao.insertObject(entity)
                else objectDao.updateObject(entity)
            }
            is Contact -> {
                val local = contactDao.getContactById(entity.id)
                if (local?.needsSync == true) return
                val safe = entity.copy(description = entity.description ?: "")
                if (local == null) contactDao.insertContact(safe)
                else contactDao.updateContact(safe)
            }
            is Material -> {
                if (projectDao.getProjectById(entity.projectId)?.needsSync == true) return
                if (materialDao.getMaterialById(entity.id) == null)
                    materialDao.insertMaterial(entity)
                else materialDao.updateMaterial(entity)
            }
            is WorkItem -> {
                if (projectDao.getProjectById(entity.projectId)?.needsSync == true) return
                if (workItemDao.getWorkItemById(entity.id) == null)
                    workItemDao.insertWorkItem(entity)
                else workItemDao.updateWorkItem(entity)
            }
            is ContactMethod -> {
                if (contactDao.getContactById(entity.contactId)?.needsSync == true) return
                if (contactMethodDao.getContactMethodById(entity.id) == null)
                    contactMethodDao.insertContactMethod(entity)
                else contactMethodDao.updateContactMethod(entity)
            }
        }
    }

    private suspend fun loadProjects(userId: String) {
        runCatching {
            val response = apiService.getAllProjects()
            if (!response.isSuccessful) {
                Log.w(TAG, "loadProjects: code=${response.code()}")
                return@runCatching
            }

            response.body()?.forEach { dto ->
                val local = projectDao.getProjectById(dto.id)
                val shouldOverwrite = local == null
                        || local.needsSync != true
                        || (dto.version ?: 0L) > (local.version ?: 0L)

                if (shouldOverwrite) {
                    upsertEntity(dto)
                } else {
                    Log.d(TAG, "loadProjects: skip ${dto.id} — local needsSync, version=${local?.version}")
                }
            }
        }.onFailure { Log.e(TAG, "loadProjects: ${it.message}", it) }
    }

    private suspend fun loadObjects() {
        runCatching {
            val response = apiService.getRootObjects()
            if (!response.isSuccessful) return@runCatching
            response.body()?.forEach { obj ->
                val local = objectDao.getObjectById(obj.id)
                if (local?.needsSync == true) return@forEach
                if (syncOperationDao.exists("OBJECT", obj.id)) {
                    Log.d(TAG, "loadObjects: skipping ${obj.id} — pending DELETE")
                    return@forEach
                }
                upsertEntity(obj)
            }
        }.onFailure { Log.e(TAG, "loadObjects: ${it.message}") }
    }

    private suspend fun loadContacts() {
        runCatching {
            val response = apiService.getAllContacts()
            if (!response.isSuccessful) return@runCatching
            response.body()?.forEach { c ->
                if (contactDao.getContactById(c.id)?.needsSync != true) upsertEntity(c)
            }
        }.onFailure { Log.e(TAG, "loadContacts: ${it.message}") }
    }

    private suspend fun loadContactMethods() {
        runCatching {
            val contacts = contactDao.getAllContactsOnce()
            val allMethods = mutableListOf<ContactMethod>()
            contacts.forEach { contact ->
                if (contact.needsSync) return@forEach
                val response = apiService.getContactMethods(contact.id)
                if (response.isSuccessful) {
                    response.body()?.let { allMethods.addAll(it) }
                }
            }
            allMethods.forEach { upsertEntity(it) }
        }.onFailure { Log.e(TAG, "loadContactMethods: ${it.message}") }
    }

    private suspend fun loadChanges() {
        runCatching {
            val response = apiService.getChangesInbox()
            if (!response.isSuccessful) return@runCatching
            changeRequestDao.upsertAll(response.body().orEmpty().map { it.toEntity() })
        }.onFailure { Log.e(TAG, "loadChanges: ${it.message}") }
    }

    private fun getEntityType(entity: Any): String = when (entity) {
        is Project -> "PROJECT"
        is ObjectModel -> "OBJECT"
        is Contact -> "CONTACT"
        is Material -> "MATERIAL"
        is WorkItem -> "WORK_ITEM"
        is ContactMethod -> "CONTACT_METHOD"
        else -> entity.javaClass.simpleName.uppercase()
    }

    private fun getEntityId(entity: Any): String = when (entity) {
        is Project -> entity.id
        is ObjectModel -> entity.id
        is Contact -> entity.id
        is Material -> entity.id
        is WorkItem -> entity.id
        is ContactMethod -> entity.id
        else -> UUID.randomUUID().toString()
    }

    private fun Material.toSnapshotDto() = MaterialSnapshotDto(
        id = id, name = name, quantity = quantity, unit = unit,
        unitPrice = unitPrice, category = category, notes = notes
    )

    private fun WorkItem.toSnapshotDto() = WorkItemSnapshotDto(
        id = id, name = name, stage = stage, laborHours = laborHours,
        hourlyRate = hourlyRate, materialCost = materialCost,
        isCompleted = isCompleted, notes = notes
    )

    private fun ChangeRequestDto.toEntity() = ChangeRequestEntity(
        id = id, projectId = projectId, projectName = projectName,
        authorId = authorId, authorName = authorName, authorPhone = authorPhone,
        kind = kind, status = status, payloadJson = payloadJson, comment = comment,
        reviewComment = reviewComment, reviewerId = reviewerId,
        reviewerName = reviewerName, reviewerPhone = reviewerPhone,
        createdAt = parseDate(createdAt), reviewedAt = reviewedAt?.let { parseDate(it) },
        version = version
    )

    private fun parseDate(value: String?): Long {
        if (value.isNullOrBlank()) return System.currentTimeMillis()
        return runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .parse(value)?.time
        }.getOrNull() ?: System.currentTimeMillis()
    }
}
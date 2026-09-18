package com.example.myapplication.services

import android.content.Context
import android.util.Log
import com.example.myapplication.data.database.ContactDao
import com.example.myapplication.data.database.ContactMethodDao
import com.example.myapplication.data.database.MaterialDao
import com.example.myapplication.data.database.ObjectDao
import com.example.myapplication.data.database.ProjectDao
import com.example.myapplication.data.database.SyncOperationDao
import com.example.myapplication.data.database.SyncOperationEntity
import com.example.myapplication.data.database.WorkItemDao
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.ObjectModel
import com.example.myapplication.data.models.Project
import com.example.myapplication.data.models.WorkItem
import com.example.myapplication.network.ApiService
import com.example.myapplication.utils.NetworkUtils
import com.example.myapplication.utils.UserPreferences
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private val userPreferences: UserPreferences,
    @ApplicationContext private val context: Context,
    private val gson: Gson,

    private val networkUtils: NetworkUtils
) {
    private companion object {
        const val TAG = "SyncManager"
        const val PERIODIC_SYNC_INTERVAL_MS = 60_000L
        const val INITIAL_RETRY_DELAY_MS = 2_000L
        const val MAX_RETRY_DELAY_MS = 30_000L
        const val MAX_RETRY_ATTEMPTS = 8
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isSyncing = AtomicBoolean(false)

    private var periodicSyncJob: Job? = null

    var onForceLogout: (() -> Unit)? = null

    fun startPeriodicSync() {
        if (periodicSyncJob?.isActive == true) return

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
        Log.d(TAG, "Periodic sync stopped")
    }

    suspend fun syncIfQueueIsEmpty(userId: String) {
        if (!isSyncing.compareAndSet(false, true)) {
            Log.d(TAG, "Sync already in progress, skipping")
            return
        }

        try {
            val pendingCount = syncOperationDao.getOperationsCountForUser(userId)
            if (pendingCount == 0) {
                syncDataFromServer(userId, skipQueueCheck = true)
                return
            }

            val allSent = syncPendingOperationsToServer(userId, retryUntilSuccess = true)
            if (allSent) {
                syncDataFromServer(userId, skipQueueCheck = true)
            }
        } finally {
            isSyncing.set(false)
        }
    }

    suspend fun handleOnlineRecovery() {
        if (!isSyncing.compareAndSet(false, true)) return

        try {
            val userId = userPreferences.getUserId() ?: return
            Log.d(TAG, "Online recovery started")

            val allSent = syncPendingOperationsToServer(userId, retryUntilSuccess = true)
            if (allSent) {
                syncDataFromServer(userId, skipQueueCheck = true)
            }
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
                is ConnectException, is SocketTimeoutException -> {
                    Log.w(TAG, "Session check: no network")
                    true
                }
                else -> {
                    Log.e(TAG, "Session check failed: ${e.message}")
                    false
                }
            }
        }
    }

    suspend fun syncDataFromServer(userId: String, skipQueueCheck: Boolean = false) {
        if (!skipQueueCheck && syncOperationDao.getOperationsCountForUser(userId) > 0) {
            Log.w(TAG, "Cannot load — queue is not empty")
            return
        }
        if (!networkUtils.isOnline(context)) {
            Log.w(TAG, "No internet connection")
            return
        }

        coroutineScope {
            listOf(
                async { loadProjects(userId) },
                async { loadObjects() },
                async { loadContacts() }
            ).forEach { it.await() }
        }
        Log.d(TAG, "Data loaded from server")
    }

    suspend fun shareProject(
        projectId: String,
        phoneNumber: String
    ): Result<Unit> = runCatching {
        val response = apiService.shareProject(
            projectId,
            mapOf("phoneNumber" to phoneNumber)
        )
        if (!response.isSuccessful) {
            throw when (response.code()) {
                404 -> ShareException.UserNotFound()
                400 -> ShareException.InvalidPhone()
                403 -> ShareException.NoPermission()
                else -> ShareException.Unknown(response.code())
            }
        }
    }

    suspend fun queueOperation(type: String, entityType: String, entityId: String, data: Any) {
        val userId = userPreferences.getUserId()
        if (userId == null) {
            Log.w(TAG, "queueOperation: no user — dropped $type $entityType")
            return
        }

        syncOperationDao.insert(
            SyncOperationEntity(
                id = UUID.randomUUID().toString(),
                type = type,
                entityType = entityType,
                entityId = entityId,
                data = gson.toJson(data),
                timestamp = System.currentTimeMillis(),
                userId = userId
            )
        )
        Log.d(TAG, "Queued: $type $entityType")
    }

    suspend fun syncEntityToServer(entity: Any) {
        val type = getEntityType(entity)
        val id = getEntityId(entity)

        try {
            val response = when (entity) {
                is Project -> apiService.createProject(entity)
                is ObjectModel -> apiService.createObject(entity)
                is Contact -> apiService.createContact(entity)
                is Material -> apiService.addMaterial(entity)
                is WorkItem -> apiService.addWorkItem(entity)
                is ContactMethod -> apiService.addContactMethod(entity)
                else -> {
                    Log.w(TAG, "Unknown entity: ${entity.javaClass.simpleName}")
                    return
                }
            }

            if (response.isSuccessful) {
                response.body()?.let { data ->
                    runCatching { upsertEntity(data) }
                        .onFailure { Log.e(TAG, "Local upsert failed: ${it.message}") }
                }
                Log.d(TAG, "Synced: ${entity.javaClass.simpleName}")
            } else {
                queueOperation("CREATE", type, id, entity)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed, queued: ${e.message}")
            queueOperation("CREATE", type, id, entity)
        }
    }

    suspend fun syncProjectToServer(project: Project) = syncEntityToServer(project)
    suspend fun syncObjectToServer(obj: ObjectModel) = syncEntityToServer(obj)
    suspend fun syncContactToServer(contact: Contact) = syncEntityToServer(contact)

    suspend fun syncProjectDeletion(projectId: String) {
        try {
            val response = apiService.deleteProject(projectId)
            if (!response.isSuccessful) {
                queueOperation("DELETE", "PROJECT", projectId, mapOf("id" to projectId))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delete failed, queued: ${e.message}")
            queueOperation("DELETE", "PROJECT", projectId, mapOf("id" to projectId))
        }
    }

    suspend fun clearAllLocalData() {
        Log.d(TAG, "Clearing all local data")
        projectDao.deleteAll()
        objectDao.deleteAll()
        contactDao.deleteAll()
        contactMethodDao.deleteAll()
        materialDao.deleteAll()
        workItemDao.deleteAll()
        syncOperationDao.clearAll()
    }

    suspend fun clearAllOperations() {
        syncOperationDao.clearAll()
    }

    suspend fun clearAllOperationsForUser(userId: String) {
        syncOperationDao.clearForUser(userId)
    }

    fun hasInternetConnection(): Boolean = networkUtils.isOnline(context)

    suspend fun getPendingOperationsCount(): Int =
        userPreferences.getUserId()?.let {
            syncOperationDao.getOperationsCountForUser(it)
        } ?: 0

    private suspend fun syncPendingOperationsToServer(
        userId: String,
        retryUntilSuccess: Boolean = false
    ): Boolean {
        var attempt = 0
        var delayMs = INITIAL_RETRY_DELAY_MS

        while (true) {
            attempt++

            if (userPreferences.getUserId() != userId) {
                Log.w(TAG, "User changed, aborting sync")
                return false
            }

            val operations = syncOperationDao.getOperationsForUser(userId)
            if (operations.isEmpty()) return true

            Log.d(TAG, "Syncing ${operations.size} operations (attempt $attempt)")

            val successIds = mutableListOf<String>()
            var hasFailures = false

            for (op in operations) {
                val success = when (op.type) {
                    "CREATE" -> handleCreateOperation(op)
                    "UPDATE" -> handleUpdateOperation(op)
                    "DELETE" -> handleDeleteOperation(op)
                    else -> false
                }
                if (success) successIds.add(op.id) else hasFailures = true
            }

            successIds.forEach { syncOperationDao.deleteById(it) }

            if (!hasFailures) return true
            if (!retryUntilSuccess) return false

            if (!networkUtils.isOnline(context)) {
                Log.w(TAG, "No network, aborting retry loop")
                return false
            }

            if (attempt >= MAX_RETRY_ATTEMPTS) {
                Log.w(TAG, "Max retry attempts ($MAX_RETRY_ATTEMPTS) reached")
                return false
            }

            delay(delayMs)
            delayMs = (delayMs * 1.5).coerceAtMost(MAX_RETRY_DELAY_MS.toDouble()).toLong()
        }
    }

    private suspend fun handleCreateOperation(op: SyncOperationEntity): Boolean {
        return try {
            val response = when (op.entityType) {
                "PROJECT" -> apiService.createProject(gson.fromJson(op.data, Project::class.java))
                "OBJECT" -> apiService.createObject(gson.fromJson(op.data, ObjectModel::class.java))
                "CONTACT" -> apiService.createContact(gson.fromJson(op.data, Contact::class.java))
                "MATERIAL" -> apiService.addMaterial(gson.fromJson(op.data, Material::class.java))
                "WORK_ITEM" -> apiService.addWorkItem(gson.fromJson(op.data, WorkItem::class.java))
                "CONTACT_METHOD" -> apiService.addContactMethod(gson.fromJson(op.data, ContactMethod::class.java))
                else -> {
                    Log.w(TAG, "Unknown entity type: ${op.entityType}")
                    return false
                }
            }

            if (response.isSuccessful) {
                response.body()?.let { data ->
                    runCatching { upsertEntity(data) }
                        .onFailure { Log.e(TAG, "Local upsert failed: ${it.message}") }
                }
                true
            } else {
                false
            }
        } catch (e: ConnectException) {
            false
        } catch (e: SocketTimeoutException) {
            false
        } catch (e: Exception) {
            Log.e(TAG, "handleCreateOperation error: ${e.message}")
            false
        }
    }

    private suspend fun handleUpdateOperation(op: SyncOperationEntity): Boolean {
        return try {
            val response = when (op.entityType) {
                "PROJECT" -> apiService.updateProject(op.entityId, gson.fromJson(op.data, Project::class.java))
                "OBJECT" -> apiService.updateObject(op.entityId, gson.fromJson(op.data, ObjectModel::class.java))
                "CONTACT" -> apiService.updateContact(op.entityId, gson.fromJson(op.data, Contact::class.java))
                else -> return false
            }

            if (response.isSuccessful) {
                response.body()?.let { data ->
                    runCatching { upsertEntity(data) }
                        .onFailure { Log.e(TAG, "Local upsert failed: ${it.message}") }
                }
                true
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "handleUpdateOperation error: ${e.message}")
            false
        }
    }

    private suspend fun handleDeleteOperation(op: SyncOperationEntity): Boolean {
        return try {
            val response = when (op.entityType) {
                "PROJECT" -> apiService.deleteProject(op.entityId)
                "OBJECT" -> apiService.deleteObject(op.entityId)
                "CONTACT" -> apiService.deleteContact(op.entityId)
                "MATERIAL" -> apiService.deleteMaterial(op.entityId)
                "WORK_ITEM" -> apiService.deleteWorkItem(op.entityId)
                else -> return false
            }
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "handleDeleteOperation error: ${e.message}")
            false
        }
    }

    private suspend fun upsertEntity(entity: Any) {
        when (entity) {
            is Project -> if (projectDao.getProjectById(entity.id) == null)
                projectDao.insertProject(entity) else projectDao.updateProject(entity)
            is ObjectModel -> if (objectDao.getObjectById(entity.id) == null)
                objectDao.insertObject(entity) else objectDao.updateObject(entity)
            is Contact -> if (contactDao.getContactById(entity.id) == null)
                contactDao.insertContact(entity) else contactDao.updateContact(entity)
            is Material -> if (materialDao.getMaterialById(entity.id) == null)
                materialDao.insertMaterial(entity) else materialDao.updateMaterial(entity)
            is WorkItem -> if (workItemDao.getWorkItemById(entity.id) == null)
                workItemDao.insertWorkItem(entity) else workItemDao.updateWorkItem(entity)
            is ContactMethod -> if (contactMethodDao.getContactMethodById(entity.id) == null)
                contactMethodDao.insertContactMethod(entity) else contactMethodDao.updateContactMethod(entity)
            else -> Log.w(TAG, "Unknown entity: ${entity.javaClass.simpleName}")
        }
    }

    private suspend fun loadProjects(userId: String) {
        runCatching {
            val response = apiService.getProjectsForUser(userId)
            if (!response.isSuccessful) return@runCatching

            val projects = response.body().orEmpty()
            projects.forEach { upsertEntity(it) }
            Log.d(TAG, "Loaded ${projects.size} projects")
        }.onFailure { Log.e(TAG, "loadProjects: ${it.message}") }
    }

    private suspend fun loadObjects() {
        runCatching {
            val response = apiService.getRootObjects()
            if (!response.isSuccessful) return@runCatching

            val rootObjects = response.body().orEmpty()
            rootObjects.forEach { upsertEntity(it) }
            Log.d(TAG, "Loaded ${rootObjects.size} root objects")
        }.onFailure { Log.e(TAG, "loadObjects: ${it.message}") }
    }

    private suspend fun loadContacts() {
        runCatching {
            val response = apiService.getAllContacts()
            if (!response.isSuccessful) return@runCatching

            val contacts = response.body().orEmpty()
            contacts.forEach { upsertEntity(it) }
            Log.d(TAG, "Loaded ${contacts.size} contacts")
        }.onFailure { Log.e(TAG, "loadContacts: ${it.message}") }
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
}
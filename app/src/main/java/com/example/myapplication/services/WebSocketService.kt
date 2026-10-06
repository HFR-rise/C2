package com.example.myapplication.services

import android.content.Context
import android.util.Log
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.database.ChangeRequestDao
import com.example.myapplication.data.database.ChangeRequestEntity
import com.example.myapplication.data.database.ContactDao
import com.example.myapplication.data.database.ContactMethodDao
import com.example.myapplication.data.database.MaterialDao
import com.example.myapplication.data.database.ObjectDao
import com.example.myapplication.data.database.ProjectDao
import com.example.myapplication.data.database.SyncOperationDao
import com.example.myapplication.data.database.WorkItemDao
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.ObjectModel
import com.example.myapplication.data.models.Project
import com.example.myapplication.data.models.ProjectDto
import com.example.myapplication.data.models.SyncMessage
import com.example.myapplication.data.models.WorkItem
import com.example.myapplication.utils.NetworkUtils
import com.example.myapplication.utils.UserPreferences
import com.google.gson.Gson
import com.google.gson.JsonElement
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class WebSocketService @Inject constructor(
    private val userPreferences: UserPreferences,
    private val projectDao: ProjectDao,
    private val materialDao: MaterialDao,
    private val workItemDao: WorkItemDao,
    private val contactDao: ContactDao,
    private val contactMethodDao: ContactMethodDao,
    private val objectDao: ObjectDao,
    private val changeRequestDao: ChangeRequestDao,
    private val syncOperationDao: SyncOperationDao,
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    @Named("websocket") private val okHttpClient: OkHttpClient,
    private val networkUtils: NetworkUtils
) {
    private companion object {
        const val TAG = "WebSocketService"
        const val MAX_RECONNECT_ATTEMPTS = 20
        const val RECONNECT_BASE_DELAY_MS = 2_000L
        const val MAX_RECONNECT_DELAY_MS = 60_000L

        const val STATUS_PENDING = "PENDING"
        const val STATUS_APPROVED = "APPROVED"
        const val STATUS_REJECTED = "REJECTED"
        const val KIND_ESTIMATE_EDIT = "ESTIMATE_EDIT"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var webSocket: WebSocket? = null

    @Volatile private var isConnected = false
    @Volatile private var isReconnecting = false
    @Volatile private var currentUserId: String? = null

    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0

    private val reconnectGuard = AtomicBoolean(false)

    var onProjectChanged: ((String) -> Unit)? = null
    var onForceLogout: (() -> Unit)? = null

    fun connect(userId: String) {
        if (userId.isEmpty()) {
            Log.w(TAG, "connect: empty userId")
            return
        }

        if (isConnected) {
            Log.d(TAG, "Already connected, reconnecting")
            disconnect()
        }

        val deviceId = userPreferences.getDeviceId()
        if (deviceId.isNullOrEmpty()) {
            Log.w(TAG, "connect: no deviceId")
            return
        }

        currentUserId = userId
        Log.d(TAG, "Connecting WebSocket for user=$userId device=$deviceId")

        val url = android.net.Uri.parse(BuildConfig.WS_BASE_URL)
            .buildUpon()
            .appendQueryParameter("userId", userId)
            .appendQueryParameter("deviceId", deviceId)
            .build()
            .toString()

        webSocket = okHttpClient.newWebSocket(
            Request.Builder().url(url).build(),
            createListener(userId)
        )
    }

    private fun createListener(userId: String) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket opened for user=$userId")
            isConnected = true
            reconnectAttempts = 0
            isReconnecting = false
            reconnectGuard.set(false)
            reconnectJob?.cancel()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text == "ping") {
                webSocket.send("pong")
                return
            }
            handleSyncMessage(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "WebSocket failure: ${t.message}")
            isConnected = false
            currentUserId?.let { startReconnect(it) }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $reason (code=$code)")
            isConnected = false

            if (code == 1000) {
                reconnectAttempts = 0
                isReconnecting = false
                reconnectGuard.set(false)
            } else {
                currentUserId?.let { startReconnect(it) }
            }
        }
    }

    private fun startReconnect(userId: String) {
        if (!reconnectGuard.compareAndSet(false, true)) {
            Log.d(TAG, "Reconnect already scheduled")
            return
        }

        isReconnecting = true
        reconnectJob?.cancel()

        reconnectJob = scope.launch {
            try {
                while (isActive && !isConnected && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    reconnectAttempts++

                    val delayMs = computeBackoffDelay(reconnectAttempts)
                    Log.d(TAG, "Reconnect attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS in ${delayMs}ms")
                    delay(delayMs)

                    if (isConnected) break
                    if (!networkUtils.isOnline(context)) {
                        Log.d(TAG, "No network, waiting...")
                        continue
                    }

                    Log.d(TAG, "Attempting reconnect")
                    disconnect()
                    connect(userId)
                }

                if (!isConnected) {
                    Log.w(TAG, "Max reconnect attempts reached, resetting counter")
                    reconnectAttempts = MAX_RECONNECT_ATTEMPTS / 2
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                isReconnecting = false
                reconnectGuard.set(false)
            }
        }
    }

    private fun computeBackoffDelay(attempt: Int): Long {
        val shift = minOf(attempt - 1, 5)
        return minOf(RECONNECT_BASE_DELAY_MS * (1L shl shift), MAX_RECONNECT_DELAY_MS)
    }

    private fun handleSyncMessage(message: String) {
        val syncMessage = runCatching { gson.fromJson(message, SyncMessage::class.java) }
            .getOrElse {
                Log.e(TAG, "Failed to parse message: ${it.message}")
                return
            }

        Log.d(TAG, "Message type=${syncMessage.type} entity=${syncMessage.entityType}")

        when (syncMessage.type) {
            "FORCE_LOGOUT" -> handleForceLogout()

            "CREATE", "UPDATE", "SHARE", "SHARE_ACCEPTED", "SHARE_DECLINED" ->
                handleDataMessage(syncMessage)

            "DELETE" -> handleDeleteMessage(syncMessage)

            "MEMBER_ADDED", "MEMBER_REMOVED", "MEMBER_UPDATED" ->
                handleMemberMessage(syncMessage)

            "CHANGE_SUBMITTED",
            "CHANGE_APPROVED",
            "CHANGE_REJECTED",
            "COMMENT_ADDED" -> handleChangeRequestMessage(syncMessage)

            else -> Log.w(TAG, "Unknown message type: ${syncMessage.type}")
        }
    }

    private fun handleForceLogout() {
        Log.w(TAG, "FORCE_LOGOUT received")
        scope.launch {
            runCatching {
                clearAllLocalData()
                disconnect()
            }.onFailure { Log.e(TAG, "ForceLogout cleanup failed: ${it.message}") }

            onForceLogout?.invoke()
        }
    }

    private fun handleDataMessage(syncMessage: SyncMessage) {
        val data = syncMessage.data ?: run {
            Log.w(TAG, "Message data is null")
            return
        }
        if (!data.isJsonObject) {
            Log.w(TAG, "Message data is not a JSON object: ${data.javaClass.simpleName}")
            return
        }

        val json = data.asJsonObject.toString()

        scope.launch {
            runCatching {
                when (syncMessage.entityType) {
                    "PROJECT" -> {
                        val dto = gson.fromJson(json, ProjectDto::class.java)
                        val local = projectDao.getProjectById(dto.id)

                        if (local?.needsSync == true) {
                            Log.d(TAG, "WS PROJECT ${dto.id}: local needsSync, skip everything")
                        } else {
                            upsertProject(dto.toEntity())
                            dto.materials?.let { list -> for (m in list) upsertMaterial(m) }
                            dto.workItems?.let { list -> for (w in list) upsertWorkItem(w) }
                        }
                    }

                    "OBJECT" -> {
                        val obj = gson.fromJson(json, ObjectModel::class.java)
                        val local = objectDao.getObjectById(obj.id)
                        if (local?.needsSync == true) {
                            Log.d(TAG, "WS OBJECT ${obj.id}: local needsSync, skip")
                        } else {
                            upsertObject(obj)
                        }
                    }

                    "CONTACT" -> {
                        val contact = gson.fromJson(json, Contact::class.java)
                        val local = contactDao.getContactById(contact.id)
                        if (local?.needsSync == true) {
                            Log.d(TAG, "WS CONTACT ${contact.id}: local needsSync, skip")
                        } else {
                            val safe = contact.copy(description = contact.description ?: "")
                            upsertContact(safe)
                        }
                    }

                    "CONTACT_METHOD" -> {
                        val method = gson.fromJson(json, ContactMethod::class.java)
                        val contact = contactDao.getContactById(method.contactId)
                        if (contact?.needsSync == true) {
                            Log.d(TAG, "WS CONTACT_METHOD ${method.id}: parent contact needsSync, skip")
                        } else {
                            upsertContactMethod(method)
                        }
                    }

                    "MATERIAL" -> {
                        val material = gson.fromJson(json, Material::class.java)
                        val project = projectDao.getProjectById(material.projectId)
                        if (project?.needsSync == true) {
                            Log.d(TAG, "WS MATERIAL ${material.id}: parent project needsSync, skip")
                        } else {
                            upsertMaterial(material)
                        }
                    }

                    "WORK_ITEM" -> {
                        val workItem = gson.fromJson(json, WorkItem::class.java)
                        val project = projectDao.getProjectById(workItem.projectId)
                        if (project?.needsSync == true) {
                            Log.d(TAG, "WS WORK_ITEM ${workItem.id}: parent project needsSync, skip")
                        } else {
                            upsertWorkItem(workItem)
                        }
                    }

                    else -> Log.w(TAG, "Unknown entity: ${syncMessage.entityType}")
                }
            }.onFailure { Log.e(TAG, "handleDataMessage failed: ${it.message}", it) }
        }
    }

    private fun handleDeleteMessage(syncMessage: SyncMessage) {
        val entityId = syncMessage.entityId
        if (entityId.isNullOrEmpty()) {
            Log.w(TAG, "Delete message without entityId")
            return
        }

        scope.launch {
            runCatching {
                when (syncMessage.entityType) {
                    "PROJECT" -> {
                        val local = projectDao.getProjectById(entityId)
                        if (local?.needsSync == true) {
                            Log.d(TAG, "WS DELETE PROJECT $entityId: local needsSync, skip")
                        } else {
                            Log.d(TAG, "WS DELETE PROJECT $entityId: deleting project + related changes")
                            projectDao.deleteProjectById(entityId)
                            changeRequestDao.deleteForProject(entityId)
                        }
                    }

                    "MATERIAL" -> {
                        val material = materialDao.getMaterialById(entityId)
                        val project = material?.let { projectDao.getProjectById(it.projectId) }
                        if (project?.needsSync == true) {
                            Log.d(TAG, "WS DELETE MATERIAL $entityId: parent project needsSync, skip")
                        } else {
                            materialDao.deleteMaterialById(entityId)
                        }
                    }

                    "WORK_ITEM" -> {
                        val workItem = workItemDao.getWorkItemById(entityId)
                        val project = workItem?.let { projectDao.getProjectById(it.projectId) }
                        if (project?.needsSync == true) {
                            Log.d(TAG, "WS DELETE WORK_ITEM $entityId: parent project needsSync, skip")
                        } else {
                            workItemDao.deleteWorkItemById(entityId)
                        }
                    }

                    "CONTACT" -> {
                        val local = contactDao.getContactById(entityId)
                        if (local?.needsSync == true) {
                            Log.d(TAG, "WS DELETE CONTACT $entityId: local needsSync, skip")
                        } else {
                            contactDao.deleteContactById(entityId)
                        }
                    }

                    "CONTACT_METHOD" -> {
                        val method = contactMethodDao.getContactMethodById(entityId)
                        val contact = method?.let { contactDao.getContactById(it.contactId) }
                        if (contact?.needsSync == true) {
                            Log.d(TAG, "WS DELETE CONTACT_METHOD $entityId: parent contact needsSync, skip")
                        } else {
                            contactMethodDao.deleteContactMethodById(entityId)
                        }
                    }

                    "OBJECT" -> {
                        val local = objectDao.getObjectById(entityId)
                        if (local?.needsSync == true) {
                            Log.d(TAG, "WS DELETE OBJECT $entityId: local needsSync, skip")
                        } else {
                            objectDao.deleteObjectById(entityId)
                        }
                    }

                    else -> Log.w(TAG, "Unknown entity for delete: ${syncMessage.entityType}")
                }
            }.onFailure { Log.e(TAG, "handleDeleteMessage failed: ${it.message}", it) }
        }
    }

    private fun handleMemberMessage(syncMessage: SyncMessage) {
        val data = syncMessage.data ?: run {
            Log.w(TAG, "Member message without data")
            return
        }
        if (!data.isJsonObject) {
            Log.w(TAG, "Member data is not a JsonObject")
            return
        }

        val obj = data.asJsonObject
        val projectId = obj.get("projectId").asStringOrNull() ?: run {
            Log.w(TAG, "Member message without projectId")
            return
        }
        val targetUserId = obj.get("userId").asStringOrNull() ?: run {
            Log.w(TAG, "Member message without userId")
            return
        }
        val role = obj.get("role").asStringOrNull()
        val currentUserId = userPreferences.getUserId()

        Log.d(TAG, "Member message: type=${syncMessage.type}, " +
                "project=$projectId, targetUser=$targetUserId, role=$role, me=$currentUserId")

        scope.launch {
            runCatching {
                when (syncMessage.type) {
                    "MEMBER_ADDED" -> {
                        if (targetUserId == currentUserId) {
                            Log.d(TAG, "I was added to project $projectId as $role — loading")
                            onProjectChanged?.invoke(projectId)
                        } else {
                            if (projectDao.getProjectById(projectId) != null) {
                                Log.d(
                                    TAG,
                                    "Member $targetUserId added to project $projectId — reloading"
                                )
                                onProjectChanged?.invoke(projectId)
                            } else {}
                        }
                    }

                    "MEMBER_REMOVED" -> {
                        if (targetUserId == currentUserId) {
                            Log.d(TAG, "I was removed from project $projectId — deleting locally")
                            projectDao.deleteProjectById(projectId)
                        } else {
                            if (projectDao.getProjectById(projectId) != null) {
                                Log.d(
                                    TAG,
                                    "Member $targetUserId removed from project $projectId — reloading"
                                )
                                onProjectChanged?.invoke(projectId)
                            } else {}
                        }
                    }

                    "MEMBER_UPDATED" -> {
                        if (projectDao.getProjectById(projectId) != null) {
                            Log.d(
                                TAG,
                                "Member $targetUserId updated in project $projectId — reloading"
                            )
                            onProjectChanged?.invoke(projectId)
                        } else if (targetUserId == currentUserId) {
                            Log.d(TAG, "I was updated in project $projectId — loading")
                            onProjectChanged?.invoke(projectId)
                        } else {}
                    }

                    else -> Log.w(TAG, "handleMemberMessage: unknown type=${syncMessage.type}")
                }
            }.onFailure {
                Log.e(TAG, "handleMemberMessage failed: ${it.message}", it)
            }
        }
    }

    private fun handleChangeRequestMessage(syncMessage: SyncMessage) {
        val data = syncMessage.data ?: run {
            Log.w(TAG, "ChangeRequest message without data")
            return
        }
        if (!data.isJsonObject) {
            Log.w(TAG, "ChangeRequest data is not a JsonObject")
            return
        }

        val obj = data.asJsonObject

        scope.launch {
            runCatching {
                val changeId = obj.get("id").asStringOrNull()
                    ?: syncMessage.entityId
                    ?: return@launch

                val projectId = obj.get("projectId").asStringOrNull()
                    ?: return@launch

                val entity = ChangeRequestEntity(
                    id = changeId,
                    projectId = projectId,
                    projectName = obj.get("projectName").asStringOrNull(),
                    authorId = obj.get("authorId").asStringOrNull() ?: "",
                    authorName = obj.get("authorName").asStringOrNull(),
                    authorPhone = obj.get("authorPhone").asStringOrNull(),
                    kind = obj.get("kind").asStringOrNull() ?: KIND_ESTIMATE_EDIT,
                    status = obj.get("status").asStringOrNull() ?: STATUS_PENDING,
                    payloadJson = obj.get("payloadJson").asStringOrNull(),
                    comment = obj.get("comment").asStringOrNull(),
                    reviewComment = obj.get("reviewComment").asStringOrNull(),
                    reviewerId = obj.get("reviewerId").asStringOrNull(),
                    reviewerName = obj.get("reviewerName").asStringOrNull(),
                    reviewerPhone = obj.get("reviewerPhone").asStringOrNull(),
                    createdAt = parseTimestamp(obj.get("createdAt").asStringOrNull()),
                    reviewedAt = obj.get("reviewedAt").asStringOrNull()?.let { parseTimestamp(it) },
                    version = obj.get("version").asLongOrNull()
                )

                changeRequestDao.upsert(entity)
                Log.d(TAG, "ChangeRequest upserted: ${entity.id} ${entity.status}")

                if (entity.kind == KIND_ESTIMATE_EDIT &&
                    (entity.status == STATUS_APPROVED || entity.status == STATUS_REJECTED)
                ) {
                    onProjectChanged?.invoke(entity.projectId)
                }
            }.onFailure {
                Log.e(TAG, "handleChangeRequestMessage failed: ${it.message}", it)
            }
        }
    }

    private fun parseTimestamp(value: String?): Long {
        if (value.isNullOrBlank()) return System.currentTimeMillis()
        return runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .parse(value)?.time
        }.getOrNull() ?: System.currentTimeMillis()
    }

    private suspend fun upsertProject(project: Project) {
        if (projectDao.getProjectById(project.id) == null) projectDao.insertProject(project)
        else projectDao.updateProject(project)
    }

    private suspend fun upsertObject(obj: ObjectModel) {
        if (objectDao.getObjectById(obj.id) == null) objectDao.insertObject(obj)
        else objectDao.updateObject(obj)
    }

    private suspend fun upsertContact(contact: Contact) {
        if (contactDao.getContactById(contact.id) == null) contactDao.insertContact(contact)
        else contactDao.updateContact(contact)
    }

    private suspend fun upsertContactMethod(method: ContactMethod) {
        if (contactMethodDao.getContactMethodById(method.id) == null)
            contactMethodDao.insertContactMethod(method)
        else
            contactMethodDao.updateContactMethod(method)
    }

    private suspend fun upsertMaterial(material: Material) {
        if (materialDao.getMaterialById(material.id) == null)
            materialDao.insertMaterial(material)
        else
            materialDao.updateMaterial(material)
    }

    private suspend fun upsertWorkItem(workItem: WorkItem) {
        if (workItemDao.getWorkItemById(workItem.id) == null)
            workItemDao.insertWorkItem(workItem)
        else
            workItemDao.updateWorkItem(workItem)
    }

    private suspend fun clearAllLocalData() {
        Log.d(TAG, "Clearing local data (FORCE_LOGOUT)")
        projectDao.deleteAll()
        objectDao.deleteAll()
        contactDao.deleteAll()
        contactMethodDao.deleteAll()
        materialDao.deleteAll()
        workItemDao.deleteAll()
        changeRequestDao.deleteAll()
        syncOperationDao.clearAll()
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting WebSocket")
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectGuard.set(false)
        isReconnecting = false
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        isConnected = false
        reconnectAttempts = 0
    }

    fun isConnected(): Boolean = isConnected
}

private fun JsonElement?.asStringOrNull(): String? =
    if (this == null || isJsonNull) null else asString

private fun JsonElement?.asLongOrNull(): Long? =
    if (this == null || isJsonNull) null else asLong